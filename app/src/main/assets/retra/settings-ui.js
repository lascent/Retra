const settingsPageMap = {
  Appearance: 'appearanceSettingsPage',
  Video: 'videoSettingsPage',
  Audio: 'audioSettingsPage',
  Layouts: 'layoutsSettingsPage',
  Misc: 'miscSettingsPage',
  Advanced: 'advancedSettingsPage',
  Fonts: 'fontsSettingsPage',
  Language: 'languageSettingsPage',
  About: 'aboutSettingsPage'
};

document.querySelectorAll('.settings-item').forEach(item => {
  item.addEventListener('click', () => {
    openSubPage(settingsPageMap[item.dataset.setting]);
    if (item.dataset.setting === 'Appearance') {
      requestAnimationFrame(() => {
        const currentThemeCard = document.querySelector('.theme-preview-card.active');
        centerThemeCard(currentThemeCard, 'auto');
      });
    }
  });
});

// v3.55 selectable UI font + edge-to-edge Settings and Recent Saves
function getNativeUiPreference(key){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.getUiPreference === 'function')) return '';
  try { return String(window.AndroidBridge.getUiPreference(String(key)) || ''); }
  catch (_) { return ''; }
}

function setNativeUiPreference(key, value){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.setUiPreference === 'function')) return false;
  try { return Boolean(window.AndroidBridge.setUiPreference(String(key), String(value))); }
  catch (_) { return false; }
}

const fontStorageKey = 'retraUiFont';
const fontOptions = [...document.querySelectorAll('.font-option')];
const settingsVersionLabel = document.getElementById('settingsVersionLabel');
const fontStacks = {
  'Poppins': 'Poppins, system-ui, -apple-system, "Segoe UI", sans-serif',
  'Inter': 'Inter, system-ui, -apple-system, "Segoe UI", sans-serif',
  'Manrope': 'Manrope, system-ui, -apple-system, "Segoe UI", sans-serif',
  'DM Sans': '"DM Sans", system-ui, -apple-system, "Segoe UI", sans-serif'
};

function normalizeFontSlug(value){
  return (value || 'Inter')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function getSavedUiFont(){
  const saved = getNativeUiPreference('font') || localStorage.getItem(fontStorageKey) || 'Inter';
  const validSaved = fontStacks[saved] ? saved : 'Inter';
  const migrationKey = 'retraInterDefaultMigrationV103';
  const migrated = getNativeUiPreference(migrationKey) || localStorage.getItem(migrationKey);
  // v1.0.3 switches the old implicit Poppins default to Inter once. Users can
  // still select Poppins afterward; the migration marker prevents re-forcing it.
  if (!migrated && validSaved === 'Poppins') {
    localStorage.setItem(migrationKey, '1');
    setNativeUiPreference(migrationKey, '1');
    localStorage.setItem(fontStorageKey, 'Inter');
    setNativeUiPreference('font', 'Inter');
    return 'Inter';
  }
  if (!migrated) {
    localStorage.setItem(migrationKey, '1');
    setNativeUiPreference(migrationKey, '1');
  }
  return validSaved;
}

function applyUiFont(fontName, { persist = false, notify = false } = {}){
  const selected = fontStacks[fontName] ? fontName : 'Inter';
  document.documentElement.style.setProperty('--app-font', fontStacks[selected]);
  document.body.dataset.uiFont = normalizeFontSlug(selected);

  fontOptions.forEach(option => {
    const active = option.dataset.fontName === selected;
    option.classList.toggle('active', active);
    option.setAttribute('aria-pressed', active ? 'true' : 'false');
  });

  if (settingsVersionLabel) {
    settingsVersionLabel.textContent = 'Retra v1.0.4';
  }

  if (persist) { localStorage.setItem(fontStorageKey, selected); setNativeUiPreference('font', selected); }
  if (notify) showToast(window.retraI18n?.format('fontSelected', { font: selected }) || `${selected} font selected`);
}

fontOptions.forEach(option => {
  option.addEventListener('click', () => {
    applyUiFont(option.dataset.fontName, { persist: true, notify: true });
  });
});

applyUiFont(getSavedUiFont());
if (!getNativeUiPreference('font')) setNativeUiPreference('font', getSavedUiFont());

// v1.0.2 — GitHub Releases update checking. Native code performs the network
// request off the UI/emulation thread; this layer only renders status/result UI.
const retraVersionLabels = [...document.querySelectorAll('[data-retra-version-label]')];
const retraVersionDetails = [...document.querySelectorAll('[data-retra-version-detail]')];
const updateStatusLabels = [...document.querySelectorAll('[data-update-status]')];
const checkUpdateButtons = [...document.querySelectorAll('[data-check-updates]')];
const appUpdateModal = document.getElementById('appUpdateModal');
const appUpdateTitle = document.getElementById('appUpdateTitle');
const appUpdateMessage = document.getElementById('appUpdateMessage');
const appUpdateNotes = document.getElementById('appUpdateNotes');
const appUpdateLaterBtn = document.getElementById('appUpdateLaterBtn');
const appUpdateInstallBtn = document.getElementById('appUpdateInstallBtn');
let pendingOfficialUpdateUrl = '';

function setUpdateStatus(text){
  updateStatusLabels.forEach(label => { label.textContent = text; });
}

function hydrateRetraVersion(){
  let versionName = '1.0.4';
  try {
    if (window.AndroidBridge && typeof window.AndroidBridge.getAppVersionInfo === 'function') {
      const info = JSON.parse(String(window.AndroidBridge.getAppVersionInfo() || '{}'));
      if (info && info.versionName) versionName = String(info.versionName);
    }
  } catch (_) {}
  retraVersionLabels.forEach(label => { label.textContent = `Retra v${versionName}`; });
  retraVersionDetails.forEach(label => { label.textContent = `Stable ${versionName}`; });
  if (settingsVersionLabel) settingsVersionLabel.textContent = `Retra v${versionName}`;
}

function closeAppUpdateModal(){
  appUpdateModal?.classList.remove('open');
  appUpdateModal?.setAttribute('aria-hidden', 'true');
}

function openAppUpdateModal(result){
  const latest = String(result.latestVersionName || '').trim();
  pendingOfficialUpdateUrl = String(result.downloadUrl || result.releaseUrl || '').trim();
  if (appUpdateTitle) appUpdateTitle.textContent = latest ? `Retra v${latest} is available` : 'A Retra update is available';
  if (appUpdateMessage) appUpdateMessage.textContent = latest
    ? `You are using Retra v${String(result.currentVersionName || '')}. Update to v${latest} for the latest fixes and improvements.`
    : 'Update Retra to get the latest fixes and improvements.';
  if (appUpdateNotes) {
    const notes = String(result.notes || '').trim();
    appUpdateNotes.textContent = notes || 'See the official GitHub release for details.';
  }
  appUpdateModal?.classList.add('open');
  appUpdateModal?.setAttribute('aria-hidden', 'false');
}

function requestRetraUpdateCheck(manual = true){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.checkForUpdates === 'function')) {
    if (manual) showToast('Update checking is available in the Android app', 'warning');
    return;
  }
  if (manual) setUpdateStatus('Checking…');
  try { window.AndroidBridge.checkForUpdates(Boolean(manual)); }
  catch (_) {
    if (manual) {
      setUpdateStatus('Could not check');
      showToast('Could not check for updates', 'error');
    }
  }
}

window.retraOnUpdateCheck = function(payloadJson, manual){
  let result = {};
  try { result = JSON.parse(String(payloadJson || '{}')); } catch (_) {}
  const status = String(result.status || 'error');

  if (status === 'checking') {
    setUpdateStatus('Checking…');
    return;
  }

  if (status === 'update_available') {
    const latest = String(result.latestVersionName || '').trim();
    setUpdateStatus(latest ? `v${latest} available` : 'Update available');
    openAppUpdateModal(result);
    return;
  }

  if (status === 'up_to_date') {
    setUpdateStatus('You’re up to date');
    if (manual) showToast('No new updates available', 'info', 2600);
    return;
  }

  if (manual) {
    setUpdateStatus('Could not check');
    showToast(String(result.message || 'Could not check for updates'), 'error', 2800);
  }
};

checkUpdateButtons.forEach(button => button.addEventListener('click', () => requestRetraUpdateCheck(true)));
appUpdateLaterBtn?.addEventListener('click', closeAppUpdateModal);
appUpdateModal?.addEventListener('click', event => { if (event.target === appUpdateModal) closeAppUpdateModal(); });
appUpdateInstallBtn?.addEventListener('click', () => {
  const url = pendingOfficialUpdateUrl;
  if (!url) {
    showToast('Update download is unavailable', 'error');
    return;
  }
  let opened = false;
  try {
    if (window.AndroidBridge && typeof window.AndroidBridge.openUpdateUrl === 'function') {
      opened = Boolean(window.AndroidBridge.openUpdateUrl(url));
    }
  } catch (_) {}
  if (!opened) {
    // Browser/debug fallback; Android WebUiController routes external HTTPS URLs
    // to the system browser rather than loading them inside Retra.
    try { window.location.href = url; opened = true; } catch (_) {}
  }
  if (opened) closeAppUpdateModal();
});

hydrateRetraVersion();
// Automatic checks are intentionally delayed until the bundled UI has settled.
// Native code runs this once per fresh Retra process. A new GitHub release is
// therefore discovered on the next cold launch without requiring the manual
// Check for updates action.
window.setTimeout(() => requestRetraUpdateCheck(false), 1400);

const appShell = document.querySelector('.app-shell');

let currentOrientationPage = 'libraryPage';

function pageAllowsLandscape(pageId){
  // Retra's normal UI is now responsive in both orientations. The native
  // Android Screen orientation setting remains the source of truth for whether
  // the Activity is actually allowed to rotate.
  return true;
}

async function applyDeviceOrientationPolicy(pageId){
  const allowLandscape = pageAllowsLandscape(pageId);
  document.body.classList.toggle('game-landscape-allowed', allowLandscape);

  // Do not call ScreenOrientation.lock()/unlock() here. Android already owns
  // the user's Portrait/Landscape/Auto setting; browser-side locking caused the
  // library to stay in a narrow portrait shell after the phone rotated.
}

function updateShellOrientation(pageId){
  currentOrientationPage = pageId || 'libraryPage';
  const isScreenEditor = currentOrientationPage === 'screenSizePage';
  const landscapeMode = isScreenEditor && currentEditorOrientation() === 'landscape';
  document.body.classList.toggle('landscape-preview-mode', landscapeMode);
  appShell?.classList.toggle('landscape-shell', landscapeMode);
  if (!isScreenEditor) {
    document.body.classList.remove('screen-editor-portrait', 'screen-editor-landscape');
  }
  applyDeviceOrientationPolicy(currentOrientationPage);
}

window.addEventListener('orientationchange', () => {
  window.setTimeout(() => {
    applyDeviceOrientationPolicy(currentOrientationPage);
    updateShellOrientation(currentOrientationPage);
  }, 80);
});

window.addEventListener('resize', () => {
  if (currentOrientationPage === 'screenSizePage') {
    updateShellOrientation(currentOrientationPage);
  } else if (!pageAllowsLandscape(currentOrientationPage)) {
    document.body.classList.remove('landscape-preview-mode');
    appShell?.classList.remove('landscape-shell');
  }
});

// Apply portrait-first behavior immediately for the normal app UI.
updateShellOrientation(document.querySelector('.page.active')?.id || 'libraryPage');
const themeModeOptions = [...document.querySelectorAll('.theme-mode-option')];
const themePreviewCards = [...document.querySelectorAll('.theme-preview-card')];
const themePreviewStrip = document.getElementById('themePreviewStrip');
const pureBlackToggle = document.getElementById('pureBlackToggle');
const translucentModeToggle = document.getElementById('translucentModeToggle');
const appearanceStorageKey = 'retraAppearanceSettings';

const appearanceDefaults = {
  mode: 'Dark',
  theme: 'Default',
  pureBlack: false,
  translucent: false
};

const appearanceState = { ...appearanceDefaults };

try {
  const storedAppearance = JSON.parse(getNativeUiPreference('appearance') || localStorage.getItem(appearanceStorageKey) || '{}');

  // v3.73 default appearance migration:
  // Old builds shipped with the Tako palette selected by default. If the
  // saved values are still exactly that old default, upgrade them to the new
  // Dark + Default setup. Any appearance the user actually customized is kept.
  const isLegacyDefaultAppearance =
    storedAppearance.mode === 'Dark' &&
    storedAppearance.theme === 'Tako' &&
    storedAppearance.pureBlack === false &&
    storedAppearance.translucent === false;

  if (!isLegacyDefaultAppearance) {
    Object.assign(appearanceState, storedAppearance);
  } else {
    localStorage.setItem(appearanceStorageKey, JSON.stringify(appearanceDefaults));
  }
} catch (error) {}

function normalizeThemeSlug(value){
  return (value || 'Default')
    .toLowerCase()
    .replace(/&/g, 'and')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .replace(/-and-/g, '-');
}

function persistAppearanceState(){
  const json = JSON.stringify(appearanceState);
  localStorage.setItem(appearanceStorageKey, json);
  setNativeUiPreference('appearance', json);
}

const systemColorScheme = window.matchMedia('(prefers-color-scheme: light)');

function getResolvedThemeMode(){
  if (appearanceState.mode === 'System') {
    return systemColorScheme.matches ? 'light' : 'dark';
  }

  return appearanceState.mode.toLowerCase();
}

function applyAppearanceState(){
  const resolvedMode = getResolvedThemeMode();

  document.body.dataset.theme = normalizeThemeSlug(appearanceState.theme);
  document.body.dataset.themeMode = appearanceState.mode.toLowerCase();
  document.body.dataset.resolvedMode = resolvedMode;
  document.body.dataset.pureBlack = appearanceState.pureBlack ? 'true' : 'false';
  document.body.dataset.translucent = appearanceState.translucent ? 'true' : 'false';

  appShell?.classList.toggle('translucent-shell', appearanceState.translucent);

  themeModeOptions.forEach(btn => {
    const active = btn.dataset.themeMode === appearanceState.mode;
    btn.classList.toggle('active', active);
    btn.setAttribute('aria-pressed', active ? 'true' : 'false');
  });

  themePreviewCards.forEach(btn => {
    const active = btn.dataset.themeName === appearanceState.theme;
    btn.classList.toggle('active', active);
    btn.setAttribute('aria-pressed', active ? 'true' : 'false');
  });

  if (pureBlackToggle) {
    pureBlackToggle.checked = !!appearanceState.pureBlack;
    pureBlackToggle.disabled = resolvedMode === 'light';
    pureBlackToggle.closest('.detail-row')?.classList.toggle(
      'appearance-disabled-row',
      resolvedMode === 'light'
    );
  }

  if (translucentModeToggle) {
    translucentModeToggle.checked = !!appearanceState.translucent;
  }
}

systemColorScheme.addEventListener?.('change', () => {
  if (appearanceState.mode === 'System') {
    applyAppearanceState();
  }
});

themeModeOptions.forEach(option => {
  option.addEventListener('click', () => {
    appearanceState.mode = option.dataset.themeMode;
    persistAppearanceState();
    applyAppearanceState();
    showToast(`${option.dataset.themeMode} mode selected`);
  });
});

function centerThemeCard(card, behavior = 'smooth'){
  if (!themePreviewStrip || !card) return;
  const maxLeft = Math.max(0, themePreviewStrip.scrollWidth - themePreviewStrip.clientWidth);
  const targetLeft = card.offsetLeft - (themePreviewStrip.clientWidth - card.offsetWidth) / 2;
  themePreviewStrip.scrollTo({
    left: Math.max(0, Math.min(maxLeft, targetLeft)),
    behavior
  });
}

themePreviewCards.forEach(card => {
  card.addEventListener('click', () => {
    appearanceState.theme = card.dataset.themeName;
    persistAppearanceState();
    applyAppearanceState();
    centerThemeCard(card, 'smooth');
    showToast(`${card.dataset.themeName} theme selected`);
  });
});

pureBlackToggle?.addEventListener('change', () => {
  appearanceState.pureBlack = pureBlackToggle.checked;
  persistAppearanceState();
  applyAppearanceState();
  showToast(pureBlackToggle.checked ? 'Pure black dark mode enabled' : 'Pure black dark mode disabled');
});

translucentModeToggle?.addEventListener('change', () => {
  appearanceState.translucent = translucentModeToggle.checked;
  persistAppearanceState();
  applyAppearanceState();
  showToast(translucentModeToggle.checked ? 'Translucent mode enabled' : 'Translucent mode disabled');
});

if (themePreviewStrip){
  let pointerDown = false;
  let activePointerId = null;
  let startX = 0;
  let startY = 0;
  let startScrollLeft = 0;
  let lastX = 0;
  let lastTime = 0;
  let velocityX = 0;
  let didDrag = false;
  let suppressClickUntil = 0;
  let scrollEndTimer = null;
  const dragThreshold = 5;
  const themeCardsInStrip = [...themePreviewStrip.querySelectorAll('.theme-preview-card')];

  function getNearestThemeCard(){
    if (!themeCardsInStrip.length) return null;
    const stripRect = themePreviewStrip.getBoundingClientRect();
    const stripCenter = stripRect.left + stripRect.width / 2;
    let closestCard = themeCardsInStrip[0];
    let closestDistance = Infinity;

    themeCardsInStrip.forEach(card => {
      const rect = card.getBoundingClientRect();
      const distance = Math.abs((rect.left + rect.width / 2) - stripCenter);
      if (distance < closestDistance) {
        closestDistance = distance;
        closestCard = card;
      }
    });

    return closestCard;
  }

  function snapThemeStripToNearestCard(behavior = 'smooth'){
    const closestCard = getNearestThemeCard();
    if (closestCard) centerThemeCard(closestCard, behavior);
  }

  // Touchscreens keep the browser's native momentum scrolling. Custom drag is
  // only used for mouse/pen so the row feels natural on both input types.
  themePreviewStrip.addEventListener('pointerdown', (event) => {
    if (event.pointerType === 'touch') return;
    if (event.button !== undefined && event.button !== 0) return;
    if (event.isPrimary === false) return;

    pointerDown = true;
    activePointerId = event.pointerId;
    didDrag = false;
    startX = lastX = event.clientX;
    startY = event.clientY;
    startScrollLeft = themePreviewStrip.scrollLeft;
    lastTime = performance.now();
    velocityX = 0;
  });

  themePreviewStrip.addEventListener('pointermove', (event) => {
    if (!pointerDown || event.pointerId !== activePointerId) return;

    const dx = event.clientX - startX;
    const dy = event.clientY - startY;

    if (!didDrag) {
      if (Math.max(Math.abs(dx), Math.abs(dy)) < dragThreshold) return;
      if (Math.abs(dy) > Math.abs(dx) * 1.15) {
        pointerDown = false;
        activePointerId = null;
        return;
      }

      didDrag = true;
      themePreviewStrip.classList.add('dragging');
      try { themePreviewStrip.setPointerCapture(event.pointerId); } catch (error) {}
    }

    const now = performance.now();
    const dt = Math.max(1, now - lastTime);
    const frameDx = event.clientX - lastX;
    velocityX = (-frameDx / dt) * 0.7 + velocityX * 0.3;
    lastX = event.clientX;
    lastTime = now;

    themePreviewStrip.scrollLeft = startScrollLeft - dx;
    if (event.cancelable) event.preventDefault();
  }, { passive: false });

  const stopDragging = (event) => {
    if (!pointerDown && !didDrag) return;
    if (event?.pointerId !== undefined && activePointerId !== null && event.pointerId !== activePointerId) return;

    if (didDrag) {
      suppressClickUntil = performance.now() + 260;
      const maxLeft = Math.max(0, themePreviewStrip.scrollWidth - themePreviewStrip.clientWidth);
      const projectedLeft = Math.max(0, Math.min(maxLeft, themePreviewStrip.scrollLeft + velocityX * 150));
      themePreviewStrip.scrollTo({ left: projectedLeft, behavior: 'smooth' });
      window.setTimeout(() => snapThemeStripToNearestCard('smooth'), 150);
    }

    if (activePointerId !== null) {
      try {
        if (themePreviewStrip.hasPointerCapture?.(activePointerId)) {
          themePreviewStrip.releasePointerCapture(activePointerId);
        }
      } catch (error) {}
    }

    pointerDown = false;
    activePointerId = null;
    didDrag = false;
    velocityX = 0;
    themePreviewStrip.classList.remove('dragging');
  };

  themePreviewStrip.addEventListener('pointerup', stopDragging);
  themePreviewStrip.addEventListener('pointercancel', stopDragging);

  // Prevent a mouse drag from accidentally selecting a theme when released.
  themePreviewStrip.addEventListener('click', (event) => {
    if (performance.now() < suppressClickUntil) {
      event.preventDefault();
      event.stopPropagation();
    }
  }, true);

  // Trackpads and mouse wheels move the theme row horizontally.
  themePreviewStrip.addEventListener('wheel', (event) => {
    const horizontalDelta = Math.abs(event.deltaX) > Math.abs(event.deltaY)
      ? event.deltaX
      : event.deltaY;

    if (Math.abs(horizontalDelta) > 0.5) {
      themePreviewStrip.scrollLeft += horizontalDelta;
      if (event.cancelable) event.preventDefault();
      clearTimeout(scrollEndTimer);
      scrollEndTimer = setTimeout(() => snapThemeStripToNearestCard('smooth'), 110);
    }
  }, { passive: false });

  // Native phone swipes get momentum from the browser, then settle neatly on
  // the closest card. The debounce also works where scrollend is unavailable.
  themePreviewStrip.addEventListener('scroll', () => {
    if (pointerDown) return;
    clearTimeout(scrollEndTimer);
    scrollEndTimer = setTimeout(() => snapThemeStripToNearestCard('smooth'), 140);
  }, { passive: true });
}


applyAppearanceState();

document.querySelectorAll('[data-more-open]').forEach(btn => {
  btn.addEventListener('click', () => openSubPage(btn.dataset.moreOpen));
});

document.querySelectorAll('[data-back-to]').forEach(btn => {
  btn.addEventListener('click', () => {
    if (document.body.classList.contains('in-game-settings') && btn.closest('#settingsHomePage')) {
      if (window.AndroidBridge && typeof window.AndroidBridge.closeInGameSettings === 'function') {
        window.AndroidBridge.closeInGameSettings();
        return;
      }
    }
    const target = btn.dataset.backTo;
    if (target === 'libraryPage' || target === 'historyPage' || target === 'morePage') {
      setMainPage(target);
    } else {
      openSubPage(target);
    }
  });
});

const RETRA_HELP_CENTER_URL = 'https://github.com/lascent/Retra/blob/main/TROUBLESHOOTING.md';

document.querySelectorAll('[data-action]').forEach(btn => {
  btn.addEventListener('click', () => {
    if (btn.dataset.openPage) {
      openSubPage(btn.dataset.openPage);
      return;
    }
    if (btn.dataset.action === 'Open help center') {
      // Navigate away from the bundled app-assets origin. WebUiController
      // intercepts external HTTPS navigation and opens it with Android's
      // ACTION_VIEW handler, so the Help Center appears in the user's browser.
      window.location.href = RETRA_HELP_CENTER_URL;
      return;
    }
    if (btn.dataset.action === 'Reset advanced settings') {
      try {
        if (window.AndroidBridge && typeof window.AndroidBridge.resetAdvancedSettings === 'function') {
          window.AndroidBridge.resetAdvancedSettings();
          setTimeout(() => { try { refreshNativeSettings(); } catch (_) {} }, 80);
        }
      } catch (_) {}
      selectedCpuCore = 'Automatic';
      selectedCartridgeSaveType = 'Automatic';
      localStorage.setItem('retraCpuCore', selectedCpuCore);
      localStorage.setItem('retraCartridgeSaveType', selectedCartridgeSaveType);
      applyCpuCore();
      applyCartridgeSaveType();
      showToast('Advanced settings restored');
      return;
    }
    showToast(btn.dataset.action);
  });
});

document.querySelectorAll('[data-open-page]').forEach(btn => {
  btn.addEventListener('click', (event) => {
    event.preventDefault();
    const targetPage = btn.dataset.openPage;
    if (targetPage === 'privacyPolicyPage' && btn.dataset.privacyReturn) {
      const privacyBackBtn = document.getElementById('privacyPolicyBackBtn');
      if (privacyBackBtn) privacyBackBtn.dataset.backTo = btn.dataset.privacyReturn;
    }
    openSubPage(targetPage);
  });
});

const screenOrientationBtn = document.getElementById('screenOrientationBtn');
const screenOrientationSummary = document.getElementById('screenOrientationSummary');
const screenOrientationModal = document.getElementById('screenOrientationModal');
const closeScreenOrientationModal = document.getElementById('closeScreenOrientationModal');
const screenOrientationOptions = [...document.querySelectorAll('[data-orientation-value]')];


const fastForwardSpeedBtn = document.getElementById('fastForwardSpeedBtn');
const fastForwardSpeedLabel = document.getElementById('fastForwardSpeedLabel');
const fastForwardSpeedModal = document.getElementById('fastForwardSpeedModal');
const closeFastForwardSpeedModal = document.getElementById('closeFastForwardSpeedModal');
const fastForwardSpeedOptions = [...document.querySelectorAll('[data-fastforward-value]')];

const glslShaderBtn = document.getElementById('glslShaderBtn');
const glslShaderLabel = document.getElementById('glslShaderLabel');
const glslShaderModal = document.getElementById('glslShaderModal');
const glslShaderOptionsEl = document.getElementById('glslShaderOptions');
const installGlslShaderBtn = document.getElementById('installGlslShaderBtn');
const closeGlslShaderModal = document.getElementById('closeGlslShaderModal');
let selectedGlslShader = 'none';
let availableGlslShaders = [
  { id: 'none', label: 'Off', builtIn: true, impact: 'None', description: 'Default renderer • no extra GPU cost' },
  { id: 'gba-color', label: 'GBA Color Corrected', builtIn: true, impact: 'Low', description: 'Balanced handheld-style color response' },
  { id: 'sharp', label: 'Sharp', builtIn: true, impact: 'Low', description: 'Crisp pixels with subtle edge detail' },
  { id: 'smooth', label: 'Smooth', builtIn: true, impact: 'Low', description: 'Soft multi-sample scaling for uneven sizes' },
  { id: 'pixel-perfect', label: 'Pixel Perfect', builtIn: true, impact: 'Low', description: 'Locks sampling to original pixel centers' },
  { id: 'lcd-grid', label: 'LCD Grid', builtIn: true, impact: 'Medium', description: 'Subtle handheld LCD cell structure' },
  { id: 'lcd-response', label: 'LCD Response', builtIn: true, impact: 'Medium', description: 'Gentle LCD-style pixel response softness' },
  { id: 'scanlines', label: 'Scanlines', builtIn: true, impact: 'Low', description: 'Light retro horizontal scanline texture' },
  { id: 'crt-lite', label: 'CRT Lite', builtIn: true, impact: 'Medium', description: 'Light curvature, scanlines and vignette' },
  { id: 'retro-warm', label: 'Retro Warm', builtIn: true, impact: 'Low', description: 'Warm palette with restrained contrast' }
];

let selectedScreenOrientation = localStorage.getItem('retraScreenOrientation') || 'Auto rotate';
let selectedFastForwardSpeed = localStorage.getItem('retraFastForwardSpeed') || '4x';

function applyScreenOrientationState(){
  if (screenOrientationSummary) {
    screenOrientationSummary.textContent = selectedScreenOrientation;
  }
  screenOrientationOptions.forEach(button => {
    button.classList.toggle('active', button.dataset.orientationValue === selectedScreenOrientation);
  });
}


function applyFastForwardSpeed(){
  if (fastForwardSpeedLabel) {
    fastForwardSpeedLabel.textContent = selectedFastForwardSpeed.replace('x', '×');
  }
  fastForwardSpeedOptions.forEach(button => {
    button.classList.toggle('active', button.dataset.fastforwardValue === selectedFastForwardSpeed);
  });
}

function renderGlslShaderOptions(){
  if (!glslShaderOptionsEl) return;
  glslShaderOptionsEl.replaceChildren();
  availableGlslShaders.forEach(option => {
    const button = document.createElement('button');
    button.className = `selection-option shader-option${option.id === selectedGlslShader ? ' active' : ''}`;
    button.type = 'button';
    button.dataset.shaderValue = option.id;

    const radio = document.createElement('span');
    radio.className = 'selection-radio';

    const copy = document.createElement('span');
    copy.className = 'shader-option-copy';
    const title = document.createElement('span');
    title.className = 'shader-option-title';
    title.textContent = option.builtIn ? option.label : `${option.label} • Installed`;
    const description = document.createElement('span');
    description.className = 'shader-option-description';
    description.textContent = option.description || (option.builtIn ? 'Built-in Retra shader' : 'Installed GLSL shader');
    copy.append(title, description);

    const impact = document.createElement('span');
    impact.className = `shader-impact shader-impact-${String(option.impact || 'Custom').toLowerCase()}`;
    impact.textContent = option.impact === 'None' ? 'No cost' : `${option.impact || 'Custom'} GPU`;

    button.append(radio, copy, impact);
    button.addEventListener('click', () => {
      selectedGlslShader = option.id;
      renderGlslShaderOptions();
      if (window.AndroidBridge && typeof window.AndroidBridge.setGlslShader === 'function') {
        window.AndroidBridge.setGlslShader(selectedGlslShader);
      }
      closeSelectionModal(glslShaderModal);
    });
    glslShaderOptionsEl.appendChild(button);
  });

  const selected = availableGlslShaders.find(option => option.id === selectedGlslShader);
  if (glslShaderLabel) {
    if (!selected || selectedGlslShader === 'none') {
      glslShaderLabel.textContent = 'Off • no extra GPU cost';
    } else {
      const impact = selected.impact && selected.impact !== 'None' ? ` • ${selected.impact} GPU` : '';
      glslShaderLabel.textContent = `${selected.label}${impact}`;
    }
  }
}

function closeSelectionModal(modal){
  modal?.classList.remove('open');
}

screenOrientationBtn?.addEventListener('click', () => {
  applyScreenOrientationState();
  screenOrientationModal?.classList.add('open');
});

closeScreenOrientationModal?.addEventListener('click', () => closeSelectionModal(screenOrientationModal));
screenOrientationModal?.addEventListener('click', (event) => {
  if (event.target === screenOrientationModal) closeSelectionModal(screenOrientationModal);
});
screenOrientationOptions.forEach(button => {
  button.addEventListener('click', () => {
    selectedScreenOrientation = button.dataset.orientationValue;
    localStorage.setItem('retraScreenOrientation', selectedScreenOrientation);
    applyScreenOrientationState();
    if (window.AndroidBridge && typeof window.AndroidBridge.setScreenOrientation === 'function') {
      window.AndroidBridge.setScreenOrientation(selectedScreenOrientation);
    }
    closeSelectionModal(screenOrientationModal);
    showToast(`Screen orientation: ${selectedScreenOrientation}`);
  });
});


glslShaderBtn?.addEventListener('click', () => {
  renderGlslShaderOptions();
  glslShaderModal?.classList.add('open');
});
closeGlslShaderModal?.addEventListener('click', () => closeSelectionModal(glslShaderModal));
glslShaderModal?.addEventListener('click', (event) => {
  if (event.target === glslShaderModal) closeSelectionModal(glslShaderModal);
});
installGlslShaderBtn?.addEventListener('click', () => {
  if (window.AndroidBridge && typeof window.AndroidBridge.installGlslShader === 'function') {
    window.AndroidBridge.installGlslShader();
  } else {
    showToast('Shader installation is available in the Android app');
  }
});

fastForwardSpeedBtn?.addEventListener('click', () => {
  applyFastForwardSpeed();
  fastForwardSpeedModal?.classList.add('open');
});

closeFastForwardSpeedModal?.addEventListener('click', () => closeSelectionModal(fastForwardSpeedModal));
fastForwardSpeedModal?.addEventListener('click', (event) => {
  if (event.target === fastForwardSpeedModal) closeSelectionModal(fastForwardSpeedModal);
});
fastForwardSpeedOptions.forEach(button => {
  button.addEventListener('click', () => {
    selectedFastForwardSpeed = button.dataset.fastforwardValue;
    localStorage.setItem('retraFastForwardSpeed', selectedFastForwardSpeed);
    applyFastForwardSpeed();
    if (window.AndroidBridge && typeof window.AndroidBridge.setFastForwardSpeed === 'function') {
      window.AndroidBridge.setFastForwardSpeed(selectedFastForwardSpeed);
    }
    closeSelectionModal(fastForwardSpeedModal);
    showToast(`Emulation speed: ${selectedFastForwardSpeed.replace('x', '×')}`);
  });
});

applyScreenOrientationState();
applyFastForwardSpeed();
// Android DataStore is authoritative on device. Native settings are pulled below
// through getSettingsState(); localStorage is only a browser/offline fallback.

// High-frequency range controls get a dedicated bridge path. Labels and purely
// visual previews update for every browser input event, while native work is
// rate-limited to one update per short interval and receives one explicit final
// commit when the gesture/key adjustment ends. This keeps WebView->JNI/DataStore
// traffic off the hot pointer path without making volume/opacity feel delayed.
function createNativeRangeDispatcher(key, intervalMs = 64){
  let pendingValue = null;
  let timer = 0;
  let lastSentAt = 0;

  const send = (commit) => {
    if (timer) {
      window.clearTimeout(timer);
      timer = 0;
    }
    if (pendingValue === null) return;
    const value = pendingValue;
    pendingValue = null;
    lastSentAt = performance.now();
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.setRangeSetting === 'function') {
        window.AndroidBridge.setRangeSetting(key, value, Boolean(commit));
      } else if (key === 'buttonsOpacity' && window.AndroidBridge && typeof window.AndroidBridge.setButtonsOpacity === 'function') {
        window.AndroidBridge.setButtonsOpacity(value);
      } else if (window.AndroidBridge && typeof window.AndroidBridge.setSetting === 'function') {
        window.AndroidBridge.setSetting(key, String(value));
      }
    } catch (_) {}
  };

  return {
    input(value){
      pendingValue = Number.parseInt(value, 10) || 0;
      const elapsed = performance.now() - lastSentAt;
      if (lastSentAt === 0 || elapsed >= intervalMs) {
        send(false);
        return;
      }
      if (!timer) timer = window.setTimeout(() => send(false), Math.max(0, intervalMs - elapsed));
    },
    commit(value){
      pendingValue = Number.parseInt(value, 10) || 0;
      send(true);
    }
  };
}

const buttonsOpacityRange = document.getElementById('buttonsOpacityRange');
const buttonsOpacityValue = document.getElementById('buttonsOpacityValue');
const buttonsOpacityStorageKey = 'retraButtonsOpacity';
const buttonsOpacityDispatch = createNativeRangeDispatcher('buttonsOpacity', 64);
let selectedButtonsOpacity = Number.parseInt(localStorage.getItem(buttonsOpacityStorageKey) || '70', 10);
if (!Number.isFinite(selectedButtonsOpacity)) selectedButtonsOpacity = 70;
selectedButtonsOpacity = Math.min(100, Math.max(25, selectedButtonsOpacity));

function applyButtonsOpacity(value, persistLocal = false){
  const opacity = Math.min(100, Math.max(25, Number.parseInt(value, 10) || 70));
  selectedButtonsOpacity = opacity;
  if (buttonsOpacityRange) buttonsOpacityRange.value = String(opacity);
  if (buttonsOpacityValue) buttonsOpacityValue.value = `${opacity}%`;

  // Preview the exact setting in the Screen Editor without fading editor-only
  // controls such as Back, Add Controller, resize handles, or selection UI.
  document.documentElement.style.setProperty('--retra-buttons-opacity', String(opacity / 100));
  if (persistLocal) localStorage.setItem(buttonsOpacityStorageKey, String(opacity));
}

buttonsOpacityRange?.addEventListener('input', () => {
  applyButtonsOpacity(buttonsOpacityRange.value, false);
  buttonsOpacityDispatch.input(buttonsOpacityRange.value);
});
buttonsOpacityRange?.addEventListener('change', () => {
  applyButtonsOpacity(buttonsOpacityRange.value, true);
  buttonsOpacityDispatch.commit(buttonsOpacityRange.value);
});

applyButtonsOpacity(selectedButtonsOpacity, false);

const frameSkipRange = document.getElementById('frameSkipRange');
const frameSkipValue = document.getElementById('frameSkipValue');
const frameSkipDispatch = createNativeRangeDispatcher('frameSkip', 72);
if (frameSkipRange && frameSkipValue){
  frameSkipRange.addEventListener('input', () => {
    frameSkipValue.value = frameSkipRange.value;
    frameSkipDispatch.input(frameSkipRange.value);
  });
  frameSkipRange.addEventListener('change', () => frameSkipDispatch.commit(frameSkipRange.value));
}

const volumeRange = document.getElementById('volumeRange');
const volumeValue = document.getElementById('volumeValue');
const volumeDispatch = createNativeRangeDispatcher('volume', 48);
if (volumeRange && volumeValue){
  volumeRange.addEventListener('input', () => {
    volumeValue.value = `${volumeRange.value}%`;
    volumeDispatch.input(volumeRange.value);
  });
  volumeRange.addEventListener('change', () => volumeDispatch.commit(volumeRange.value));
}

const soundFrequencyBtn = document.getElementById('soundFrequencyBtn');
const frequencyOptions = document.getElementById('frequencyOptions');
const soundFrequencyLabel = document.getElementById('soundFrequencyLabel');
const frequencyOptionButtons = [...document.querySelectorAll('.frequency-option')];

let selectedSoundFrequency = localStorage.getItem('retraSoundFrequency') || '44100';

function applySoundFrequency(){
  soundFrequencyLabel.textContent = `${selectedSoundFrequency} Hz`;

  frequencyOptionButtons.forEach(button => {
    button.classList.toggle(
      'active',
      button.dataset.frequency === selectedSoundFrequency
    );
  });
}

soundFrequencyBtn?.addEventListener('click', () => {
  const open = frequencyOptions.classList.toggle('open');
  soundFrequencyBtn.classList.toggle('open', open);
});

frequencyOptionButtons.forEach(button => {
  button.addEventListener('click', () => {
    selectedSoundFrequency = button.dataset.frequency;
    localStorage.setItem('retraSoundFrequency', selectedSoundFrequency);
    try { window.AndroidBridge?.setSetting?.('soundFrequency', selectedSoundFrequency); } catch (_) {}
    applySoundFrequency();
    frequencyOptions.classList.remove('open');
    soundFrequencyBtn?.classList.remove('open');
    showToast(`Sound frequency: ${selectedSoundFrequency} Hz`);
  });
});

applySoundFrequency();

applyFastForwardSpeed();

const fastForwardButtonBtn = document.getElementById('fastForwardButtonBtn');
const fastForwardButtonLabel = document.getElementById('fastForwardButtonLabel');
const fastForwardButtonModal = document.getElementById('fastForwardButtonModal');
const closeFastForwardButtonModal = document.getElementById('closeFastForwardButtonModal');
const fastForwardButtonOptions = [...document.querySelectorAll('[data-fastforward-button-value]')];

const cpuCoreBtn = document.getElementById('cpuCoreBtn');
const cpuCoreLabel = document.getElementById('cpuCoreLabel');
const cpuCoreModal = document.getElementById('cpuCoreModal');
const closeCpuCoreModal = document.getElementById('closeCpuCoreModal');
const cpuCoreOptions = [...document.querySelectorAll('[data-cpu-core-value]')];

const cartridgeSaveTypeBtn = document.getElementById('cartridgeSaveTypeBtn');
const cartridgeSaveTypeLabel = document.getElementById('cartridgeSaveTypeLabel');
const cartridgeSaveTypeModal = document.getElementById('cartridgeSaveTypeModal');
const closeCartridgeSaveTypeModal = document.getElementById('closeCartridgeSaveTypeModal');
const cartridgeSaveTypeOptions = [...document.querySelectorAll('[data-cartridge-save-value]')];

const importSavesBtn = document.getElementById('importSavesBtn');
const importSavesModal = document.getElementById('importSavesModal');
const selectImportFolderBtn = document.getElementById('selectImportFolderBtn');
const skipImportSavesBtn = document.getElementById('skipImportSavesBtn');
const openAppFolderBtn = document.getElementById('openAppFolderBtn');
const cloudSyncToggle = document.getElementById('cloudSyncToggle');
const cloudSyncSummary = document.getElementById('cloudSyncSummary');
const cloudBackupNowBtn = document.getElementById('cloudBackupNowBtn');
const cloudBackupNowSummary = document.getElementById('cloudBackupNowSummary');
const syncSettingsBtn = document.getElementById('syncSettingsBtn');
const syncSettingsSummary = document.getElementById('syncSettingsSummary');
const enableCheatsToggle = document.getElementById('enableCheatsToggle');
const controllerSoundToggle = document.getElementById('controllerSoundToggle');
const controllerHapticsToggle = document.getElementById('controllerHapticsToggle');
const romPatchingToggle = document.getElementById('romPatchingToggle');
const automaticArtworkToggle = document.getElementById('automaticArtworkToggle');
const artworkWifiOnlyToggle = document.getElementById('artworkWifiOnlyToggle');
const retryArtworkBtn = document.getElementById('retryArtworkBtn');
const autoSaveLoadToggle = document.getElementById('autoSaveLoadToggle');
const confirmCloseResetToggle = document.getElementById('confirmCloseResetToggle');
const fullScreenModeToggle = document.getElementById('fullScreenModeToggle');
const immersiveModeToggle = document.getElementById('immersiveModeToggle');
const stretchToFitToggle = document.getElementById('stretchToFitToggle');
const hardwareRenderingToggle = document.getElementById('hardwareRenderingToggle');
const linearFilteringToggle = document.getElementById('linearFilteringToggle');
const enableSoundToggle = document.getElementById('enableSoundToggle');
const biosToggle = document.getElementById('biosToggle');
const bootBiosToggle = document.getElementById('bootBiosToggle');
const bootBiosRow = document.getElementById('bootBiosRow');
const biosFileSummary = document.getElementById('biosFileSummary');
const smcCheckRange = document.getElementById('smcCheckRange');
const smcCheckValue = document.getElementById('smcCheckValue');
const speedHackToggle = document.getElementById('speedHackToggle');
const mosaicEffectToggle = document.getElementById('mosaicEffectToggle');
const colorStyleOptions = [...document.querySelectorAll('[data-color-style]')];
const colorStyleMoreSummary = document.getElementById('colorStyleMoreSummary');
const colorStyleStorageKey = 'retraColorStyle';
const colorStyleLabels = { classic: 'Classic', vivid: 'Vivid', warm: 'Warm', muted: 'Muted' };
let selectedColorStyle = localStorage.getItem(colorStyleStorageKey) || 'classic';

function normalizeColorStyle(value){
  const token = String(value || '').toLowerCase();
  return colorStyleLabels[token] ? token : 'classic';
}

function applyColorStyleUi(value, { persist = false, notify = false } = {}){
  selectedColorStyle = normalizeColorStyle(value);
  colorStyleOptions.forEach(option => {
    const active = option.dataset.colorStyle === selectedColorStyle;
    option.classList.toggle('active', active);
    option.setAttribute('aria-pressed', active ? 'true' : 'false');
  });
  if (colorStyleMoreSummary) colorStyleMoreSummary.textContent = colorStyleLabels[selectedColorStyle];
  if (persist) localStorage.setItem(colorStyleStorageKey, selectedColorStyle);
  if (notify) showToast(`Color Style: ${colorStyleLabels[selectedColorStyle]}`);
}

let selectedFastForwardButtonMode = localStorage.getItem('retraFastForwardButtonMode') || 'Press to toggle';
let selectedCpuCore = localStorage.getItem('retraCpuCore') || 'Automatic';
let selectedCartridgeSaveType = localStorage.getItem('retraCartridgeSaveType') || 'Automatic';

function setNativeSetting(key, value){
  try {
    if (window.AndroidBridge && typeof window.AndroidBridge.setSetting === 'function') {
      window.AndroidBridge.setSetting(String(key), String(value));
      return true;
    }
  } catch (_) {}
  localStorage.setItem(`retraSetting.${key}`, String(value));
  return false;
}

function applyNativeSettingsState(state){
  if (!state || typeof state !== 'object') return;
  const bool = (el, key, fallback) => { if (el) el.checked = key in state ? !!state[key] : fallback; };
  bool(cloudSyncToggle, 'cloudSync', false);
  bool(enableCheatsToggle, 'enableCheats', true);
  bool(controllerSoundToggle, 'controllerSound', true);
  bool(controllerHapticsToggle, 'controllerHaptics', true);
  bool(romPatchingToggle, 'romPatching', true);
  bool(automaticArtworkToggle, 'automaticArtwork', true);
  bool(artworkWifiOnlyToggle, 'artworkWifiOnly', false);
  bool(autoSaveLoadToggle, 'autoSaveLoad', true);
  bool(confirmCloseResetToggle, 'confirmCloseReset', true);
  bool(fullScreenModeToggle, 'fullScreenMode', true);
  bool(immersiveModeToggle, 'immersiveMode', true);
  bool(stretchToFitToggle, 'stretchToFit', false);
  bool(hardwareRenderingToggle, 'hardwareRendering', true);
  bool(linearFilteringToggle, 'linearFiltering', false);
  bool(enableSoundToggle, 'enableSound', true);
  bool(biosToggle, 'useBios', false);
  bool(bootBiosToggle, 'bootBios', false);
  bool(speedHackToggle, 'speedOptimization', true);
  bool(mosaicEffectToggle, 'mosaicEffect', true);
  applyColorStyleUi(state.colorStyle || selectedColorStyle);

  if (bootBiosToggle) bootBiosToggle.disabled = !biosToggle?.checked;
  bootBiosRow?.classList.toggle('disabled-row', !biosToggle?.checked);
  if (biosFileSummary && state.biosFileLabel) biosFileSummary.textContent = state.biosFileLabel;

  if (state.screenOrientation) {
    selectedScreenOrientation = String(state.screenOrientation);
    localStorage.setItem('retraScreenOrientation', selectedScreenOrientation);
    applyScreenOrientationState();
  }
  const nativeSpeed = state.emulationSpeed || state.fastForwardSpeed;
  if (nativeSpeed) {
    selectedFastForwardSpeed = String(nativeSpeed);
    localStorage.setItem('retraFastForwardSpeed', selectedFastForwardSpeed);
    applyFastForwardSpeed();
  }
  if (Array.isArray(state.glslShaderOptions)) {
    availableGlslShaders = state.glslShaderOptions.filter(option => option && option.id && option.label);
  }
  if (state.glslShader) selectedGlslShader = String(state.glslShader);
  renderGlslShaderOptions();
  if (state.buttonsOpacity != null) {
    selectedButtonsOpacity = Math.min(100, Math.max(25, Number.parseInt(state.buttonsOpacity, 10) || 70));
    localStorage.setItem(buttonsOpacityStorageKey, String(selectedButtonsOpacity));
    applyButtonsOpacity(selectedButtonsOpacity, false);
  }

  if (state.fastForwardButtonMode) {
    selectedFastForwardButtonMode = state.fastForwardButtonMode;
    applyFastForwardButtonMode();
  }
  selectedCpuCore = state.cpuCore || selectedCpuCore;
  selectedCartridgeSaveType = state.cartridgeSaveType || selectedCartridgeSaveType;
  applyCpuCore();
  applyCartridgeSaveType();

  if (frameSkipRange && state.frameSkip != null) {
    frameSkipRange.value = String(state.frameSkip);
    if (frameSkipValue) frameSkipValue.value = String(state.frameSkip);
  }
  if (volumeRange && state.volume != null) {
    volumeRange.value = String(state.volume);
    if (volumeValue) volumeValue.value = `${state.volume}%`;
  }
  if (state.soundFrequency) {
    selectedSoundFrequency = String(state.soundFrequency);
    applySoundFrequency();
  }
  if (smcCheckRange && state.smcCheck != null) {
    smcCheckRange.value = String(state.smcCheck);
    if (smcCheckValue) smcCheckValue.value = String(state.smcCheck);
  }

  const cloudConnected = !!state.cloudFolderConnected;
  const cloudAutoEnabled = !!state.cloudSync;
  const cloudAccount = String(state.cloudAccount || '').trim();
  const cloudLastBackupAt = Math.max(0, Number(state.cloudLastBackupAt) || 0);
  const cloudLastError = String(state.cloudLastSyncError || '').trim();
  const cloudTransferActive = !!state.cloudTransferActive;
  const cloudTransferLabel = String(state.cloudTransferLabel || '').trim();
  const cloudTransferProgress = Math.max(0, Math.min(100, Number(state.cloudTransferProgress) || 0));
  const cloudBackupTime = cloudLastBackupAt > 0
    ? new Date(cloudLastBackupAt).toLocaleString([], { month:'short', day:'numeric', hour:'numeric', minute:'2-digit' })
    : '';
  if (cloudSyncSummary) {
    if (cloudTransferActive) cloudSyncSummary.textContent = `${cloudTransferLabel || 'Google Drive'} • ${cloudTransferProgress}%`;
    else if (!cloudConnected) cloudSyncSummary.textContent = 'Choose a Google account to back up directly to My Drive/Retra Backups';
    else if (!cloudAutoEnabled) cloudSyncSummary.textContent = `${cloudAccount} • automatic backup off`;
    else if (cloudLastError && cloudLastBackupAt > 0) cloudSyncSummary.textContent = `${cloudAccount} • last backup ${cloudBackupTime} • needs attention`;
    else if (cloudLastError) cloudSyncSummary.textContent = `${cloudAccount} • backup needs attention`;
    else if (cloudLastBackupAt > 0) cloudSyncSummary.textContent = `${cloudAccount} • last successful backup ${cloudBackupTime}`;
    else cloudSyncSummary.textContent = `${cloudAccount} • Drive API connected • waiting for first backup`;
  }
  if (cloudBackupNowSummary) {
    if (cloudTransferActive) cloudBackupNowSummary.textContent = `${cloudTransferLabel || 'Working'} • ${cloudTransferProgress}%`;
    else if (cloudLastError) cloudBackupNowSummary.textContent = cloudLastError;
    else if (cloudLastBackupAt > 0) cloudBackupNowSummary.textContent = `Last successful backup ${cloudBackupTime}`;
    else cloudBackupNowSummary.textContent = 'Upload immediately to My Drive/Retra Backups';
  }
  if (syncSettingsBtn) {
    syncSettingsBtn.disabled = !cloudConnected;
    syncSettingsBtn.classList.toggle('disabled-row', !cloudConnected);
  }
  if (syncSettingsSummary) {
    if (!cloudConnected) syncSettingsSummary.textContent = 'Connect a Google account to manage Drive backup';
    else if (cloudLastError) syncSettingsSummary.textContent = `${cloudAccount} • ${cloudLastError}`;
    else syncSettingsSummary.textContent = `${cloudAccount} • Backup Now, Restore, change account, or disconnect`;
  }
}

window.retraNativeSettingsChanged = function(payload){
  try {
    const state = typeof payload === 'string' ? JSON.parse(payload) : payload;
    applyNativeSettingsState(state);
  } catch (_) {}
};

function refreshNativeSettings(){
  try {
    if (window.AndroidBridge && typeof window.AndroidBridge.getSettingsState === 'function') {
      const raw = window.AndroidBridge.getSettingsState();
      applyNativeSettingsState(JSON.parse(raw || '{}'));
    }
  } catch (_) {}
}

function applyFastForwardButtonMode(){
  if (fastForwardButtonLabel) fastForwardButtonLabel.textContent = selectedFastForwardButtonMode;
  fastForwardButtonOptions.forEach(button => {
    button.classList.toggle('active', button.dataset.fastforwardButtonValue === selectedFastForwardButtonMode);
  });
}

function applyCpuCore(){
  if (cpuCoreLabel) cpuCoreLabel.textContent = selectedCpuCore;
  cpuCoreOptions.forEach(button => {
    button.classList.toggle('active', button.dataset.cpuCoreValue === selectedCpuCore);
  });
}

function applyCartridgeSaveType(){
  if (cartridgeSaveTypeLabel) cartridgeSaveTypeLabel.textContent = selectedCartridgeSaveType;
  cartridgeSaveTypeOptions.forEach(button => {
    button.classList.toggle('active', button.dataset.cartridgeSaveValue === selectedCartridgeSaveType);
  });
}

fastForwardButtonBtn?.addEventListener('click', () => {
  applyFastForwardButtonMode();
  fastForwardButtonModal?.classList.add('open');
});
closeFastForwardButtonModal?.addEventListener('click', () => closeSelectionModal(fastForwardButtonModal));
fastForwardButtonModal?.addEventListener('click', event => {
  if (event.target === fastForwardButtonModal) closeSelectionModal(fastForwardButtonModal);
});
fastForwardButtonOptions.forEach(button => {
  button.addEventListener('click', () => {
    selectedFastForwardButtonMode = button.dataset.fastforwardButtonValue;
    localStorage.setItem('retraFastForwardButtonMode', selectedFastForwardButtonMode);
    setNativeSetting('fastForwardButtonMode', selectedFastForwardButtonMode);
    applyFastForwardButtonMode();
    closeSelectionModal(fastForwardButtonModal);
    showToast(`Fast-forward button: ${selectedFastForwardButtonMode}`);
  });
});

cpuCoreBtn?.addEventListener('click', () => {
  applyCpuCore();
  cpuCoreModal?.classList.add('open');
});
closeCpuCoreModal?.addEventListener('click', () => closeSelectionModal(cpuCoreModal));
cpuCoreModal?.addEventListener('click', event => {
  if (event.target === cpuCoreModal) closeSelectionModal(cpuCoreModal);
});
cpuCoreOptions.forEach(button => {
  button.addEventListener('click', () => {
    selectedCpuCore = button.dataset.cpuCoreValue;
    localStorage.setItem('retraCpuCore', selectedCpuCore);
    setNativeSetting('cpuCore', selectedCpuCore);
    applyCpuCore();
    closeSelectionModal(cpuCoreModal);
    showToast(`CPU profile: ${selectedCpuCore}`);
  });
});

cartridgeSaveTypeBtn?.addEventListener('click', () => {
  applyCartridgeSaveType();
  cartridgeSaveTypeModal?.classList.add('open');
});
closeCartridgeSaveTypeModal?.addEventListener('click', () => closeSelectionModal(cartridgeSaveTypeModal));
cartridgeSaveTypeModal?.addEventListener('click', event => {
  if (event.target === cartridgeSaveTypeModal) closeSelectionModal(cartridgeSaveTypeModal);
});
cartridgeSaveTypeOptions.forEach(button => {
  button.addEventListener('click', () => {
    selectedCartridgeSaveType = button.dataset.cartridgeSaveValue;
    localStorage.setItem('retraCartridgeSaveType', selectedCartridgeSaveType);
    setNativeSetting('cartridgeSaveType', selectedCartridgeSaveType);
    applyCartridgeSaveType();
    closeSelectionModal(cartridgeSaveTypeModal);
    showToast(`Cartridge save type: ${selectedCartridgeSaveType}`);
  });
});

importSavesBtn?.addEventListener('click', () => {
  importSavesModal?.classList.add('open');
});
importSavesModal?.addEventListener('click', event => {
  if (event.target === importSavesModal) closeSelectionModal(importSavesModal);
});
selectImportFolderBtn?.addEventListener('click', () => {
  closeSelectionModal(importSavesModal);
  if (window.AndroidBridge && typeof window.AndroidBridge.importSavesFromFolder === 'function') {
    window.AndroidBridge.importSavesFromFolder();
  } else {
    showToast('Folder import is available in the Android app');
  }
});
skipImportSavesBtn?.addEventListener('click', () => {
  closeSelectionModal(importSavesModal);
});
openAppFolderBtn?.addEventListener('click', () => {
  if (window.AndroidBridge && typeof window.AndroidBridge.openAppFolder === 'function') {
    window.AndroidBridge.openAppFolder();
  } else {
    showToast('App folder is available in the Android app');
  }
});

cloudSyncToggle?.addEventListener('change', () => {
  if (window.AndroidBridge && typeof window.AndroidBridge.setCloudSyncEnabled === 'function') {
    window.AndroidBridge.setCloudSyncEnabled(cloudSyncToggle.checked);
  } else {
    setNativeSetting('cloudSync', cloudSyncToggle.checked);
  }
});
cloudBackupNowBtn?.addEventListener('click', () => {
  if (window.AndroidBridge && typeof window.AndroidBridge.backupToGoogleDrive === 'function') {
    window.AndroidBridge.backupToGoogleDrive();
  } else {
    showToast('Google Drive backup is available in the Android app');
  }
});
syncSettingsBtn?.addEventListener('click', () => {
  if (!syncSettingsBtn.disabled && window.AndroidBridge && typeof window.AndroidBridge.openCloudSyncSettings === 'function') {
    window.AndroidBridge.openCloudSyncSettings();
  }
});

[
  [enableCheatsToggle, 'enableCheats'],
  [controllerSoundToggle, 'controllerSound'],
  [controllerHapticsToggle, 'controllerHaptics'],
  [romPatchingToggle, 'romPatching'],
  [automaticArtworkToggle, 'automaticArtwork'],
  [artworkWifiOnlyToggle, 'artworkWifiOnly'],
  [autoSaveLoadToggle, 'autoSaveLoad'],
  [confirmCloseResetToggle, 'confirmCloseReset'],
  [fullScreenModeToggle, 'fullScreenMode'],
  [immersiveModeToggle, 'immersiveMode'],
  [stretchToFitToggle, 'stretchToFit'],
  [hardwareRenderingToggle, 'hardwareRendering'],
  [linearFilteringToggle, 'linearFiltering'],
  [enableSoundToggle, 'enableSound'],
  [speedHackToggle, 'speedOptimization'],
  [mosaicEffectToggle, 'mosaicEffect']
].forEach(([el, key]) => el?.addEventListener('change', () => setNativeSetting(key, el.checked)));

automaticArtworkToggle?.addEventListener('change', () => {
  automaticArtworkRequested.clear();
  if (automaticArtworkToggle.checked) scheduleAutomaticCoverLookups(false);
});
artworkWifiOnlyToggle?.addEventListener('change', () => {
  automaticArtworkRequested.clear();
  if (automaticArtworkToggle?.checked !== false) scheduleAutomaticCoverLookups(false);
});
retryArtworkBtn?.addEventListener('click', () => {
  automaticArtworkRequested.clear();
  scheduleAutomaticCoverLookups(true);
  showToast('Searching for missing artwork in the background');
});

colorStyleOptions.forEach(option => {
  option.addEventListener('click', () => {
    const style = normalizeColorStyle(option.dataset.colorStyle);
    applyColorStyleUi(style, { persist: true, notify: true });
    setNativeSetting('colorStyle', style);
  });
});

biosToggle?.addEventListener('change', () => {
  setNativeSetting('useBios', biosToggle.checked);
  if (bootBiosToggle) {
    bootBiosToggle.disabled = !biosToggle.checked;
    if (!biosToggle.checked) bootBiosToggle.checked = false;
  }
  bootBiosRow?.classList.toggle('disabled-row', !biosToggle.checked);
  if (!biosToggle.checked) setNativeSetting('bootBios', false);
});
bootBiosToggle?.addEventListener('change', () => setNativeSetting('bootBios', bootBiosToggle.checked));
const smcCheckDispatch = createNativeRangeDispatcher('smcCheck', 96);
smcCheckRange?.addEventListener('input', () => {
  if (smcCheckValue) smcCheckValue.value = smcCheckRange.value;
  smcCheckDispatch.input(smcCheckRange.value);
});
smcCheckRange?.addEventListener('change', () => smcCheckDispatch.commit(smcCheckRange.value));

applyFastForwardButtonMode();
applyCpuCore();
applyCartridgeSaveType();
applyColorStyleUi(selectedColorStyle);
refreshNativeSettings();


// v1.0 Data & Storage — selective portable .retra backups.
const dataStorageOpenFolderBtn = document.getElementById('dataStorageOpenFolderBtn');
const createBackupStartBtn = document.getElementById('createBackupStartBtn');
const restoreBackupBtn = document.getElementById('restoreBackupBtn');
const restoreCloudBackupBtn = document.getElementById('restoreCloudBackupBtn');
const confirmCreateBackupBtn = document.getElementById('confirmCreateBackupBtn');
const backupOptionInputs = [...document.querySelectorAll('[data-backup-key]')];
const dataStorageFreeText = document.getElementById('dataStorageFreeText');
const dataStorageTotalText = document.getElementById('dataStorageTotalText');
const dataStorageMeterFill = document.getElementById('dataStorageMeterFill');

function refreshDataStorageSummary(){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.getStorageSummary === 'function')) return;
  try {
    const raw = window.AndroidBridge.getStorageSummary();
    const state = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {});
    const available = Math.max(0, Number(state.availableGb) || 0);
    const total = Math.max(0, Number(state.totalGb) || 0);
    if (dataStorageFreeText) dataStorageFreeText.textContent = `${Math.round(available)} GB free`;
    if (dataStorageTotalText) dataStorageTotalText.textContent = total > 0
      ? `Available: ${Math.round(available)} GB • Total: ${Math.round(total)} GB`
      : 'Retra uses your phone\'s internal storage';
    if (dataStorageMeterFill && total > 0) {
      const usedRatio = Math.max(0, Math.min(1, (total - available) / total));
      dataStorageMeterFill.style.width = `${Math.round(usedRatio * 100)}%`;
    }
  } catch (_) {}
}

function selectedBackupOptions(){
  const result = {};
  backupOptionInputs.forEach(input => { result[input.dataset.backupKey] = Boolean(input.checked); });
  return result;
}

function refreshBackupCreateButton(){
  if (!confirmCreateBackupBtn) return;
  confirmCreateBackupBtn.disabled = !backupOptionInputs.some(input => input.checked);
}

backupOptionInputs.forEach(input => input.addEventListener('change', refreshBackupCreateButton));
refreshBackupCreateButton();

document.querySelector('[data-more-open="dataStoragePage"]')?.addEventListener('click', () => {
  window.setTimeout(refreshDataStorageSummary, 0);
});

dataStorageOpenFolderBtn?.addEventListener('click', () => {
  if (window.AndroidBridge && typeof window.AndroidBridge.openAppFolder === 'function') {
    window.AndroidBridge.openAppFolder();
  }
});

createBackupStartBtn?.addEventListener('click', () => openSubPage('createBackupPage'));

confirmCreateBackupBtn?.addEventListener('click', () => {
  const selected = selectedBackupOptions();
  if (!Object.values(selected).some(Boolean)) {
    showToast('Choose at least one item to back up');
    return;
  }
  // Flush portable history/statistics metadata before native metadata files are
  // generated so a backup captures the newest Started/Recent information.
  try { if (typeof savePlayHistory === 'function') savePlayHistory(); } catch (_) {}
  try { if (typeof syncRomCompletionStateToNative === 'function') syncRomCompletionStateToNative(); } catch (_) {}
  if (window.AndroidBridge && typeof window.AndroidBridge.createBackup === 'function') {
    window.AndroidBridge.createBackup(JSON.stringify(selected));
  }
});

restoreBackupBtn?.addEventListener('click', () => {
  if (window.AndroidBridge && typeof window.AndroidBridge.restoreBackup === 'function') {
    window.AndroidBridge.restoreBackup();
  }
});

if (restoreCloudBackupBtn) {
  restoreCloudBackupBtn.addEventListener('click', function () {
    if (window.AndroidBridge && typeof window.AndroidBridge.restoreFromGoogleDrive === 'function') {
      window.AndroidBridge.restoreFromGoogleDrive();
    } else {
      showToast('Google Drive recovery is available in the Android app');
    }
  });
}

window.retraBackupRestored = function(){
  try { refreshNativeSettings(); } catch (_) {}
  try {
    const restoredAppearance = JSON.parse(getNativeUiPreference('appearance') || '{}');
    Object.assign(appearanceState, appearanceDefaults, restoredAppearance);
    applyAppearanceState();
    applyUiFont(getSavedUiFont(), { persist: false, notify: false });
  } catch (_) {}
  try {
    hydrateLibraryFromNativeRoom();
    renderLibraryFromStorage({ force: true });
    if (typeof renderHistory === 'function') renderHistory();
    if (typeof updateStatistics === 'function') updateStatistics();
    if (currentRomCard) {
      refreshRomRecentSaves(currentRomCard);
      updateRomLaunchAction(currentRomCard);
    }
  } catch (_) {}
  refreshDataStorageSummary();
};
