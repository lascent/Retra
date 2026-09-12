/* v4.06 — Real working layout profiles.
   Each profile owns independent Portrait + Landscape controller/screen data.
   The currently selected profile is mirrored into the existing native gameplay
   keys, so gameplay and the Screen Editor continue using the same source of truth. */
const layoutProfilesStorageKey = 'retraLayoutProfilesV406';
const layoutProfilesList = document.getElementById('layoutProfilesList');
const addLayoutProfileBtn = document.getElementById('addLayoutProfileBtn');
const editActiveLayoutProfileBtn = document.getElementById('editActiveLayoutProfileBtn');
const layoutProfileModal = document.getElementById('layoutProfileModal');
const layoutProfileNameInput = document.getElementById('layoutProfileNameInput');
const cancelLayoutProfileBtn = document.getElementById('cancelLayoutProfileBtn');
const saveLayoutProfileBtn = document.getElementById('saveLayoutProfileBtn');
const screenEditorBackBtn = document.querySelector('#screenSizePage .back-btn');

function deepCloneLayoutValue(value){
  if (value == null) return null;
  try { return JSON.parse(JSON.stringify(value)); }
  catch (_) { return null; }
}

function readNativeGameplayBundle(orientation){
  if (orientation !== 'portrait' && orientation !== 'landscape') return { controller:null, screen:null };
  if (!(window.AndroidBridge && typeof window.AndroidBridge.getGameplayLayout === 'function')) {
    return { controller:null, screen:null };
  }
  try {
    const raw = nativeGetGameplayLayout(orientation);
    const parsed = typeof raw === 'string' ? JSON.parse(raw || '{}') : raw;
    if (!parsed || typeof parsed !== 'object') return { controller:null, screen:null };
    return {
      controller: parsed.controller && typeof parsed.controller === 'object' ? parsed.controller : null,
      screen: parsed.screen && typeof parsed.screen === 'object' ? parsed.screen : null
    };
  } catch (_) {
    return { controller:null, screen:null };
  }
}

function readCurrentGameplayBundle(orientation){
  const nativeBundle = readNativeGameplayBundle(orientation);
  let controller = nativeBundle.controller;
  let screen = nativeBundle.screen;

  if (!controller) {
    try {
      const local = JSON.parse(localStorage.getItem(currentPreviewLayoutStorageKey(orientation)) || 'null');
      if (local && typeof local === 'object' && (!local.orientation || local.orientation === orientation)) {
        controller = local;
      }
    } catch (_) {}
  }

  if (!screen) {
    try {
      screen = buildScreenLayoutPayload(orientation);
    } catch (_) {
      screen = { orientation, mode:'best', customFrame:null };
    }
  }

  if (controller && typeof controller === 'object') controller.orientation = orientation;
  if (screen && typeof screen === 'object') screen.orientation = orientation;

  return {
    controller: deepCloneLayoutValue(controller),
    screen: deepCloneLayoutValue(screen)
  };
}

function createInitialLayoutProfileStore(){
  return {
    version:1,
    activeId:'default',
    profiles:[{
      id:'default',
      name:'Default',
      isDefault:true,
      createdAt:Date.now(),
      layouts:{
        portrait:readCurrentGameplayBundle('portrait'),
        landscape:readCurrentGameplayBundle('landscape')
      }
    }]
  };
}

function loadLayoutProfileStore(){
  try {
    const parsed = JSON.parse(localStorage.getItem(layoutProfilesStorageKey) || 'null');
    if (parsed && Array.isArray(parsed.profiles) && parsed.profiles.length) {
      if (!parsed.profiles.some(profile => profile.id === 'default')) {
        parsed.profiles.unshift(createInitialLayoutProfileStore().profiles[0]);
      }
      if (!parsed.profiles.some(profile => profile.id === parsed.activeId)) parsed.activeId = 'default';
      parsed.profiles.forEach(profile => {
        if (!profile.layouts || typeof profile.layouts !== 'object') profile.layouts = {};
        if (!('portrait' in profile.layouts)) profile.layouts.portrait = { controller:null, screen:null };
        if (!('landscape' in profile.layouts)) profile.layouts.landscape = { controller:null, screen:null };
      });
      return parsed;
    }
  } catch (_) {}
  const fresh = createInitialLayoutProfileStore();
  localStorage.setItem(layoutProfilesStorageKey, JSON.stringify(fresh));
  return fresh;
}

let layoutProfileStore = loadLayoutProfileStore();

function saveLayoutProfileStore(){
  localStorage.setItem(layoutProfilesStorageKey, JSON.stringify(layoutProfileStore));
}

function getLayoutProfile(profileId){
  return layoutProfileStore.profiles.find(profile => profile.id === profileId) || null;
}

function getActiveLayoutProfile(){
  return getLayoutProfile(layoutProfileStore.activeId) || getLayoutProfile('default');
}

function captureActiveLayoutProfileFromRuntime(){
  const profile = getActiveLayoutProfile();
  if (!profile) return;
  profile.layouts = profile.layouts || {};
  ['portrait','landscape'].forEach(orientation => {
    profile.layouts[orientation] = readCurrentGameplayBundle(orientation);
  });
  saveLayoutProfileStore();
}

function clearRuntimeGameplayLayout(orientation){
  if (window.AndroidBridge && typeof window.AndroidBridge.clearGameplayLayout === 'function') {
    try { nativeClearGameplayLayout(orientation); } catch (_) {}
  }
  localStorage.removeItem(currentPreviewLayoutStorageKey(orientation));
  screenSizeState[orientation] = 'best';
  screenSizeState.customFrames[orientation] = null;
}

function applyLayoutProfileToRuntime(profile){
  if (!profile) return;

  ['portrait','landscape'].forEach(orientation => {
    const bundle = profile.layouts?.[orientation] || {};
    const controller = deepCloneLayoutValue(bundle.controller);
    const screen = deepCloneLayoutValue(bundle.screen);

    if (!controller && !screen) {
      clearRuntimeGameplayLayout(orientation);
      return;
    }

    // A missing controller or screen means that part of this profile uses the
    // untouched Retra default. Clear the old active profile first so data from
    // one profile can never leak into another, then restore whichever half this
    // profile explicitly owns below.
    if (!controller || !screen) {
      clearRuntimeGameplayLayout(orientation);
    }

    if (controller) {
      controller.orientation = orientation;
      localStorage.setItem(currentPreviewLayoutStorageKey(orientation), JSON.stringify(controller));
    } else {
      localStorage.removeItem(currentPreviewLayoutStorageKey(orientation));
    }

    if (screen) {
      screen.orientation = orientation;
      const mode = ['fullscreen','centered','best','stretch','custom'].includes(screen.mode) ? screen.mode : 'best';
      screenSizeState[orientation] = mode;
      const custom = screen.customFrame && typeof screen.customFrame === 'object'
        ? deepCloneLayoutValue(screen.customFrame)
        : null;
      screenSizeState.customFrames[orientation] = custom;
    } else {
      screenSizeState[orientation] = 'best';
      screenSizeState.customFrames[orientation] = null;
    }

    if (window.AndroidBridge && controller && screen && typeof window.AndroidBridge.commitGameplayLayout === 'function') {
      try {
        nativeCommitGameplayLayout(JSON.stringify(controller), JSON.stringify(screen));
      } catch (_) {}
    } else {
      if (window.AndroidBridge && controller && typeof window.AndroidBridge.setControllerLayout === 'function') {
        try { nativeSetControllerLayout(JSON.stringify(controller)); } catch (_) {}
      }
      if (window.AndroidBridge && screen && typeof window.AndroidBridge.setEmulatorScreenLayout === 'function') {
        try { nativeSetScreenLayout(JSON.stringify(screen)); } catch (_) {}
      }
    }
  });

  screenSizeState.orientation = currentEditorOrientation();
  localStorage.setItem(screenSizeStorageKey, JSON.stringify(screenSizeState));

  // Force the next Screen Editor entry to rebuild from the newly selected
  // profile rather than keeping the previous profile's live DOM state.
  removeExtraPreviewControls();
  previewLayoutState.controls = {};
  previewLayoutState.selectedId = 'dpad';
  previewLayoutInitialized = false;
  previewLayoutInitializedOrientation = null;
  previewExtraControlCounter = 0;
  clearBasePreviewControlInlineGeometry();
}

function activateLayoutProfile(profileId, { notify = true } = {}){
  const next = getLayoutProfile(profileId);
  if (!next) return false;

  if (layoutProfileStore.activeId !== profileId) {
    captureActiveLayoutProfileFromRuntime();
    layoutProfileStore.activeId = profileId;
    saveLayoutProfileStore();
    applyLayoutProfileToRuntime(next);
  }

  renderLayoutProfiles();
  if (notify) showToast(`${next.name} selected`);
  return true;
}

function renderLayoutProfiles(){
  if (!layoutProfilesList) return;
  layoutProfilesList.innerHTML = '';

  layoutProfileStore.profiles.forEach(profile => {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'layout-card layout-profile-card';
    card.dataset.layoutProfileId = profile.id;
    if (profile.id === layoutProfileStore.activeId) card.classList.add('selected');

    const copy = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = profile.name || 'Layout';
    const subtitle = document.createElement('span');
    subtitle.textContent = profile.isDefault ? 'Standard GBA controls' : 'Custom controller layout';
    copy.append(title, subtitle);

    const radio = document.createElement('div');
    radio.className = 'radio-dot';
    radio.setAttribute('aria-hidden', 'true');

    card.append(copy, radio);
    card.addEventListener('click', () => activateLayoutProfile(profile.id));
    layoutProfilesList.appendChild(card);
  });
}

function openLayoutProfileModal(){
  if (!layoutProfileModal || !layoutProfileNameInput) return;
  layoutProfileNameInput.value = '';
  layoutProfileModal.classList.add('open');
  layoutProfileModal.setAttribute('aria-hidden', 'false');
  setTimeout(() => layoutProfileNameInput.focus(), 80);
}

function closeLayoutProfileModal(){
  layoutProfileModal?.classList.remove('open');
  layoutProfileModal?.setAttribute('aria-hidden', 'true');
}

function openActiveLayoutProfileEditor(){
  const active = getActiveLayoutProfile();
  if (!active) return;
  if (screenEditorBackBtn) screenEditorBackBtn.dataset.backTo = 'layoutsSettingsPage';
  openSubPage('screenSizePage');
}

function createLayoutProfileFromInput(){
  const name = (layoutProfileNameInput?.value || '').trim();
  if (!name) {
    showToast('Enter a layout name');
    return;
  }
  if (layoutProfileStore.profiles.some(profile => (profile.name || '').toLowerCase() === name.toLowerCase())) {
    showToast('Layout name already exists');
    return;
  }

  // Save the profile currently in use before cloning it. This makes a newly
  // created profile start from exactly what the user currently sees/uses.
  captureActiveLayoutProfileFromRuntime();
  const source = getActiveLayoutProfile();
  const id = `layout-${Date.now().toString(36)}-${Math.random().toString(36).slice(2,7)}`;
  const profile = {
    id,
    name,
    isDefault:false,
    createdAt:Date.now(),
    layouts:deepCloneLayoutValue(source?.layouts) || {
      portrait:{ controller:null, screen:null },
      landscape:{ controller:null, screen:null }
    }
  };

  layoutProfileStore.profiles.push(profile);
  layoutProfileStore.activeId = id;
  saveLayoutProfileStore();
  applyLayoutProfileToRuntime(profile);
  renderLayoutProfiles();
  closeLayoutProfileModal();

  // My Boy!-style flow: Name -> OK -> directly edit that profile.
  if (screenEditorBackBtn) screenEditorBackBtn.dataset.backTo = 'layoutsSettingsPage';
  openSubPage('screenSizePage');
}

addLayoutProfileBtn?.addEventListener('click', openLayoutProfileModal);
editActiveLayoutProfileBtn?.addEventListener('click', openActiveLayoutProfileEditor);
cancelLayoutProfileBtn?.addEventListener('click', closeLayoutProfileModal);
saveLayoutProfileBtn?.addEventListener('click', createLayoutProfileFromInput);
layoutProfileNameInput?.addEventListener('keydown', event => {
  if (event.key === 'Enter') createLayoutProfileFromInput();
  if (event.key === 'Escape') closeLayoutProfileModal();
});
layoutProfileModal?.addEventListener('click', event => {
  if (event.target === layoutProfileModal) closeLayoutProfileModal();
});

// Screen Size opened from Video should still return to Video. A profile edit
// changes this target to Layouts until the next normal Video entry.
screenSizeBtn?.addEventListener('click', () => {
  if (screenEditorBackBtn) screenEditorBackBtn.dataset.backTo = 'videoSettingsPage';
});

// Extend the existing editor auto-save transaction so Back also updates the
// currently selected profile's own Portrait/Landscape copy.
const commitScreenEditorStateV405 = commitScreenEditorState;
commitScreenEditorState = function(){
  commitScreenEditorStateV405();
  captureActiveLayoutProfileFromRuntime();
  renderLayoutProfiles();
};

// On startup, make the selected profile authoritative again. This is needed
// after a full app restart because native gameplay keys are intentionally the
// active-profile mirror, not a second profile database.
const startupLayoutProfile = getActiveLayoutProfile();
if (startupLayoutProfile) applyLayoutProfileToRuntime(startupLayoutProfile);
renderLayoutProfiles();
