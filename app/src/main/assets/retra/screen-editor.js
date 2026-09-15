const screenSizeBtn = document.getElementById('screenSizeBtn');
const screenSizeSummary = document.getElementById('screenSizeSummary');
const screenPreviewShell = document.getElementById('screenPreviewShell');
const emulatorPreview = document.getElementById('emulatorPreview');
const previewScreenFrame = document.getElementById('previewScreenFrame');
const screenPreviewSurface = document.getElementById('previewScreenSurface');
const screenContextMenu = document.getElementById('screenContextMenu');
const screenSizeModeButtons = [...document.querySelectorAll('.screen-context-option[data-size-mode]')];
const addControlFab = document.getElementById('addControlFab');
const screenGridToggleBtn = document.getElementById('screenGridToggleBtn');
const previewScaleHandle = document.getElementById('previewScaleHandle');
const previewScaleValue = document.getElementById('previewScaleValue');
const controlRemoveMenu = document.getElementById('controlRemoveMenu');
const removeSelectedControlBtn = document.getElementById('removeSelectedControlBtn');
const addControlModal = document.getElementById('addControlModal');
const addControlList = document.getElementById('addControlList');
const addControlItems = [...document.querySelectorAll('.add-control-item')];
const closeAddControlModalBtn = document.getElementById('closeAddControlModalBtn');
const resetPreviewLayoutBtn = document.getElementById('resetPreviewLayoutBtn');
const screenSizeStorageKey = 'retraScreenSizeStateV388';
const screenEditorGridStorageKey = 'retraScreenEditorGridV1';
const previewLayoutStorageKeyBase = 'retraPreviewButtonsLayoutV390';
const screenSizeLabels = {
  fullscreen: 'Make fullscreen',
  centered: 'Make centered',
  betterfit: 'Best Fit',
  best: 'Best scaling (4x)',
  stretch: 'Break aspect ratio',
  custom: 'Custom resize'
};
let screenEditorGridEnabled = localStorage.getItem(screenEditorGridStorageKey) === '1';
function applyScreenEditorGrid(){
  emulatorPreview?.classList.toggle('screen-grid-enabled', screenEditorGridEnabled);
  if (screenGridToggleBtn) {
    screenGridToggleBtn.classList.toggle('active', screenEditorGridEnabled);
    screenGridToggleBtn.setAttribute('aria-pressed', screenEditorGridEnabled ? 'true' : 'false');
  }
}
screenGridToggleBtn?.addEventListener('click', event => {
  event.preventDefault();
  event.stopPropagation();
  screenEditorGridEnabled = !screenEditorGridEnabled;
  localStorage.setItem(screenEditorGridStorageKey, screenEditorGridEnabled ? '1' : '0');
  applyScreenEditorGrid();
});
const DEFAULT_CONTROLLER_SCALE_PORTRAIT = 1.0;
const DEFAULT_CONTROLLER_SCALE_LANDSCAPE = 1.0;
function defaultControllerScale(orientation = currentEditorOrientation()){
  return orientation === 'portrait' ? DEFAULT_CONTROLLER_SCALE_PORTRAIT : DEFAULT_CONTROLLER_SCALE_LANDSCAPE;
}
function currentEditorOrientation(){
  // Prefer the visual viewport in Android WebView because innerWidth/innerHeight
  // can briefly report the previous orientation while the system bars are
  // settling. Portrait and landscape are treated as two independent canvases.
  const width = Math.max(1, Number(window.visualViewport?.width) || window.innerWidth || 1);
  const height = Math.max(1, Number(window.visualViewport?.height) || window.innerHeight || 1);
  return height >= width ? 'portrait' : 'landscape';
}
function currentPreviewLayoutStorageKey(orientation = currentEditorOrientation()){
  return `${previewLayoutStorageKeyBase}_${orientation}`;
}
const screenSizeState = {
  orientation: currentEditorOrientation(),
  landscape: 'best',
  portrait: 'best',
  customFrames: { landscape: null, portrait: null }
};
try {
  const savedState = JSON.parse(localStorage.getItem(screenSizeStorageKey) || '{}');
  if (savedState && typeof savedState === 'object') {
    ['landscape', 'portrait'].forEach(orientation => {
      const savedMode = savedState[orientation] || 'best';
      screenSizeState[orientation] = ['fullscreen','centered','betterfit','best','stretch','custom'].includes(savedMode) ? savedMode : 'best';
      const custom = savedState.customFrames?.[orientation] || null;
      if (custom && typeof custom === 'object') {
        const left = Number(custom.left);
        const top = Number(custom.top);
        const width = Number(custom.width);
        const height = Number(custom.height);
        if ([left, top, width, height].every(Number.isFinite) && width > 0.08 && height > 0.08) {
          screenSizeState.customFrames[orientation] = { left, top, width, height };
        }
      }
      if (screenSizeState[orientation] === 'custom' && !screenSizeState.customFrames[orientation]) {
        screenSizeState[orientation] = 'best';
      }
    });
  }
} catch (error) {}
function currentScreenMode(orientation = currentEditorOrientation()){
  return screenSizeState[orientation] || 'best';
}
function setCurrentScreenMode(mode){
  screenSizeState[currentEditorOrientation()] = mode;
}
function currentCustomFrame(orientation = currentEditorOrientation()){
  return screenSizeState.customFrames[orientation] || null;
}
function setCurrentCustomFrame(frame){
  screenSizeState.customFrames[currentEditorOrientation()] = frame;
}
function buildScreenLayoutPayload(orientation = currentEditorOrientation()){
  return {
    orientation,
    mode: currentScreenMode(orientation),
    customFrame: currentCustomFrame(orientation)
  };
}
function hydrateEditorOrientationFromNative(orientation = currentEditorOrientation()){
  if (orientation !== 'portrait' && orientation !== 'landscape') return false;
  if (!window.AndroidBridge || typeof window.AndroidBridge.getGameplayLayout !== 'function') return false;
  try {
    const raw = nativeGetGameplayLayout(orientation);
    const bundle = JSON.parse(raw || '{}');
    if (bundle.orientation && bundle.orientation !== orientation) return false;
    if (bundle.controller && typeof bundle.controller === 'object') {
      bundle.controller.orientation = orientation;
      localStorage.setItem(currentPreviewLayoutStorageKey(orientation), JSON.stringify(bundle.controller));
    }
    if (bundle.screen && typeof bundle.screen === 'object') {
      const mode = bundle.screen.mode;
      screenSizeState[orientation] = ['fullscreen','centered','betterfit','best','stretch','custom'].includes(mode) ? mode : 'best';
      const custom = bundle.screen.customFrame;
      if (custom && typeof custom === 'object') {
        const left = Number(custom.left);
        const top = Number(custom.top);
        const width = Number(custom.width);
        const height = Number(custom.height);
        if ([left, top, width, height].every(Number.isFinite) && width > 0.08 && height > 0.08) {
          screenSizeState.customFrames[orientation] = { left, top, width, height };
        } else {
          screenSizeState.customFrames[orientation] = null;
        }
      } else {
        screenSizeState.customFrames[orientation] = null;
      }
      if (screenSizeState[orientation] === 'custom' && !screenSizeState.customFrames[orientation]) {
        screenSizeState[orientation] = 'best';
      }
      localStorage.setItem(screenSizeStorageKey, JSON.stringify(screenSizeState));
    }
    return true;
  } catch (_) {
    return false;
  }
}
function persistScreenSizeState(orientation = currentEditorOrientation()){
  if (orientation !== 'portrait' && orientation !== 'landscape') return;
  screenSizeState.orientation = orientation;
  localStorage.setItem(screenSizeStorageKey, JSON.stringify(screenSizeState));
  if (window.AndroidBridge && typeof window.AndroidBridge.setEmulatorScreenLayout === 'function') {
    nativeSetScreenLayout(JSON.stringify(buildScreenLayoutPayload(orientation)));
  }
}
function updateScreenSizeSummary(){
  if (!screenSizeSummary) return;
  const orientation = currentEditorOrientation();
  const name = orientation === 'portrait' ? 'Portrait' : 'Landscape';
  screenSizeSummary.textContent = `${name}: ${screenSizeLabels[currentScreenMode()] || screenSizeLabels.best}`;
}
function fitGbaScreen(maxWidth, maxHeight, aspect = 3 / 2){
  let width = Math.max(1, maxWidth);
  let height = width / aspect;
  if (height > maxHeight) {
    height = Math.max(1, maxHeight);
    width = height * aspect;
  }
  return { width, height };
}
function updateResponsiveScreenFrame(){
  if (!emulatorPreview || !previewScreenFrame) return;
  const previewWidth = emulatorPreview.clientWidth;
  const previewHeight = emulatorPreview.clientHeight;
  if (!previewWidth || !previewHeight) return;
  const orientation = currentEditorOrientation();
  const mode = currentScreenMode();
  let width = previewWidth;
  let height = previewHeight;
  let left = previewWidth / 2;
  let top = previewHeight / 2;
  let transform = 'translate(-50%, -50%)';
  if (mode === 'custom' && currentCustomFrame()) {
    const custom = currentCustomFrame();
    const minWidth = Math.max(96, previewWidth * 0.20);
    const minHeight = Math.max(72, previewHeight * 0.20);
    width = clampPreviewValue((Number(custom.width) || 1) * previewWidth, minWidth, previewWidth);
    height = clampPreviewValue((Number(custom.height) || 1) * previewHeight, minHeight, previewHeight);
    left = clampPreviewValue((Number(custom.left) || 0) * previewWidth, 0, Math.max(0, previewWidth - width));
    top = clampPreviewValue((Number(custom.top) || 0) * previewHeight, 0, Math.max(0, previewHeight - height));
    transform = 'none';
    // Rendering/restoring a saved frame is read-only. Do not write clamped or
    // re-normalized coordinates back into the saved orientation merely because
    // the viewport changed (rotation, browser chrome, safe-area, etc.). The
    // frame is only changed by an explicit Screen Editor resize action.
  } else if (mode === 'fullscreen' || mode === 'stretch') {
    width = previewWidth;
    height = previewHeight;
    left = 0;
    top = 0;
    transform = 'none';
  } else if (orientation === 'portrait') {
    // Portrait keeps the screen wide and near the top. Best Fit keeps a
    // little more vertical room for controls while preserving the GBA ratio.
    const widthRatio =
      mode === 'centered' ? 0.78 :
      mode === 'betterfit' ? 0.965 :
      0.985;
    const heightRatio = mode === 'betterfit' ? 0.46 : 0.42;
    ({ width, height } = fitGbaScreen(
      previewWidth * widthRatio,
      previewHeight * heightRatio,
      3 / 2
    ));
    left = (previewWidth - width) / 2;
    top = mode === 'centered' ? previewHeight * 0.035 : 0;
    transform = 'none';
  } else {
    // Best Fit mirrors the supplied landscape reference: the 3:2 game
    // image occupies almost the full usable height and about 68% of the
    // landscape width, leaving balanced controller zones on both sides.
    const widthRatio =
      mode === 'centered' ? 0.60 :
      mode === 'betterfit' ? 0.68 :
      0.90;
    const heightRatio =
      mode === 'centered' ? 0.64 :
      mode === 'betterfit' ? 1.00 :
      0.90;
    ({ width, height } = fitGbaScreen(
      previewWidth * widthRatio,
      previewHeight * heightRatio,
      3 / 2
    ));
  }
  width = Math.max(1, Math.round(width));
  height = Math.max(1, Math.round(height));
  previewScreenFrame.style.width = `${width}px`;
  previewScreenFrame.style.height = `${height}px`;
  previewScreenFrame.style.left = `${Math.round(left)}px`;
  previewScreenFrame.style.top = `${Math.round(top)}px`;
  previewScreenFrame.style.right = 'auto';
  previewScreenFrame.style.bottom = 'auto';
  previewScreenFrame.style.transform = transform;
}
const previewControlCatalog = {
  menu: { label: 'Menu', isBase: true },
  shoulderLeft: { label: 'Left shoulder', isBase: true },
  shoulderRight: { label: 'Right shoulder', isBase: true },
  dpad: { label: 'D-pad', isBase: true },
  startSelect: { label: 'Start / Select', isBase: true },
  ab: { label: 'A / B buttons', isBase: true },
  buttonA: { label: 'Button A' },
  buttonB: { label: 'Button B' },
  comboAB: { label: 'A+B' },
  comboLR: { label: 'TL+TR' },
  turboAB: { label: 'A/B turbo' },
  comboLA: { label: 'TL+A' },
  comboLB: { label: 'TL+B' },
  comboRA: { label: 'TR+A' },
  comboRB: { label: 'TR+B' },
  quickLoad: { label: 'Quick load' },
  quickSave: { label: 'Quick save' },
  fastForward: { label: 'Fast forward' },
  screenshot: { label: 'Screenshot' }
};
const previewLayoutDefaults = {};
const previewControlElements = new Map();
let screenEditorOrientationSwitchInProgress = false;
let screenEditorOrientationGeneration = 0;
function clearBasePreviewControlInlineGeometry(){
  document.querySelectorAll('#screenSizePage [data-layout-control]:not(.extra-control)').forEach(element => {
    ['left','top','right','bottom','transform'].forEach(property => element.style.removeProperty(property));
    element.classList.remove('is-selected');
    delete element.dataset.baseWidth;
    delete element.dataset.baseHeight;
  });
  previewScaleHandle?.classList.remove('visible', 'screen-scale-mode');
  previewScaleValue?.classList.remove('visible');
}
const previewLayoutState = {
  selectedId: 'dpad',
  controls: {}
};
let previewLayoutInitialized = false;
let previewLayoutInitializedOrientation = null;
let previewExtraControlCounter = 0;
function clampPreviewValue(value, min, max){
  if (Number.isNaN(value)) return min;
  if (max < min) return min;
  return Math.min(Math.max(value, min), max);
}
function renderRestoreDefaultControlsSection(){
  if (!addControlList) return;
  addControlList.querySelector('.restore-default-controls')?.remove();
  const hiddenBaseIds = Object.entries(previewLayoutState.controls)
    .filter(([controlId, state]) => state?.base && state.hidden === true && previewControlCatalog[controlId]?.isBase)
    .map(([controlId]) => controlId);
  if (!hiddenBaseIds.length) {
    refreshAddControlAvailability();
    return;
  }
  const section = document.createElement('div');
  section.className = 'restore-default-controls';
  const title = document.createElement('div');
  title.className = 'restore-default-controls-title';
  title.textContent = 'Restore default controls';
  section.appendChild(title);
  hiddenBaseIds.forEach(controlId => {
    const button = document.createElement('button');
    button.className = 'add-control-item restore-default-control-item';
    button.type = 'button';
    button.dataset.restoreControlId = controlId;
    button.innerHTML = makeAddControlItemInnerMarkup(controlId);
    button.addEventListener('click', () => restorePreviewBaseControl(controlId));
    section.appendChild(button);
  });
  addControlList.prepend(section);
  refreshAddControlAvailability();
}
function restorePreviewBaseControl(controlId){
  if (!ensurePreviewLayoutInitialized()) return;
  const state = previewLayoutState.controls[controlId];
  const element = previewControlElements.get(controlId);
  const defaults = previewLayoutDefaults[controlId];
  if (!state || !state.base || !element || !defaults) return;
  removeConflictingExtraPreviewControls(state.type || controlId, controlId);
  state.hidden = false;
  if (typeof state.x !== 'number') state.x = defaults.x;
  if (typeof state.y !== 'number') state.y = defaults.y;
  if (typeof state.scale !== 'number') state.scale = defaults.scale || defaultControllerScale(previewLayoutInitializedOrientation || currentEditorOrientation());
  element.classList.remove('is-hidden');
  applyPreviewLayoutControls();
  selectPreviewControl(controlId);
  persistPreviewLayoutState();
  renderRestoreDefaultControlsSection();
  closeAddControlModal();
  showToast(`${state.label || previewControlCatalog[controlId]?.label || 'Control'} restored`);
}
let previewControlLongPressTimer = null;
let previewControlLongPressState = null;
let ignorePreviewControlClickUntil = 0;
function clearPreviewControlLongPress(){
  if (previewControlLongPressTimer) {
    clearTimeout(previewControlLongPressTimer);
    previewControlLongPressTimer = null;
  }
  previewControlLongPressState = null;
}
function hideControlRemoveMenu(){
  controlRemoveMenu?.classList.remove('open');
  controlRemoveMenu?.setAttribute('aria-hidden', 'true');
  if (controlRemoveMenu) delete controlRemoveMenu.dataset.controlId;
}
function positionControlRemoveMenu(controlId){
  if (!controlRemoveMenu || !emulatorPreview) return;
  const element = previewControlElements.get(controlId);
  if (!element || element.classList.contains('is-hidden')) return;
  const previewRect = emulatorPreview.getBoundingClientRect();
  const controlRect = element.getBoundingClientRect();
  const menuWidth = controlRemoveMenu.offsetWidth || 132;
  const menuHeight = controlRemoveMenu.offsetHeight || 38;
  let left = controlRect.left - previewRect.left + (controlRect.width - menuWidth) / 2;
  let top = controlRect.top - previewRect.top - menuHeight - 9;
  // If there is no room above, place it directly below the held control.
  if (top < 6) top = controlRect.bottom - previewRect.top + 9;
  left = clampPreviewValue(left, 6, Math.max(6, previewRect.width - menuWidth - 6));
  top = clampPreviewValue(top, 6, Math.max(6, previewRect.height - menuHeight - 6));
  controlRemoveMenu.style.left = `${left}px`;
  controlRemoveMenu.style.top = `${top}px`;
}
function openControlRemoveMenu(controlId){
  if (!ensurePreviewLayoutInitialized()) return;
  const state = previewLayoutState.controls[controlId];
  if (!state || state.hidden) return;
  stopPreviewDrag();
  selectPreviewControl(controlId);
  controlRemoveMenu.dataset.controlId = controlId;
  controlRemoveMenu.classList.add('open');
  controlRemoveMenu.setAttribute('aria-hidden', 'false');
  positionControlRemoveMenu(controlId);
  ignorePreviewControlClickUntil = Date.now() + 650;
}
function beginPreviewControlLongPress(controlId, event){
  clearPreviewControlLongPress();
  previewControlLongPressState = {
    controlId,
    startX: event.clientX,
    startY: event.clientY
  };
  previewControlLongPressTimer = setTimeout(() => {
    const current = previewControlLongPressState;
    previewControlLongPressTimer = null;
    previewControlLongPressState = null;
    if (!current || current.controlId !== controlId) return;
    openControlRemoveMenu(controlId);
  }, 480);
}
function removePreviewControl(controlId){
  if (!ensurePreviewLayoutInitialized()) return;
  const state = previewLayoutState.controls[controlId];
  const element = previewControlElements.get(controlId);
  if (!state || !element) return;
  const label = state.label || previewControlCatalog[state.type]?.label || 'Control';
  if (state.base) {
    // Built-in controls stay in the layout data so Reset layout can restore them.
    state.hidden = true;
    element.classList.add('is-hidden');
  } else {
    element.remove();
    previewControlElements.delete(controlId);
    delete previewLayoutState.controls[controlId];
  }
  if (previewLayoutState.selectedId === controlId) previewLayoutState.selectedId = null;
  previewScaleHandle?.classList.remove('visible');
  previewScaleValue?.classList.remove('visible');
  hideControlRemoveMenu();
  persistPreviewLayoutState();
  renderRestoreDefaultControlsSection();
  refreshAddControlAvailability();
  showToast(`${label} removed`);
}
function attachPreviewControlEvents(control){
  if (!control || control.dataset.eventsBound === '1') return;
  control.dataset.eventsBound = '1';
  control.addEventListener('pointerdown', event => {
    if (event.button !== undefined && event.button !== 0) return;
    if (event.target.closest('#previewScaleHandle')) return;
    hideControlRemoveMenu();
    event.preventDefault();
    event.stopPropagation();
    beginPreviewControlLongPress(control.dataset.layoutControl, event);
    beginPreviewControlDrag(control.dataset.layoutControl, event);
  });
  ['pointerup', 'pointercancel'].forEach(eventName => {
    control.addEventListener(eventName, () => {
      // Pointer capture owns the gesture; movement tolerance separates drag from hold.
      if (previewControlLongPressTimer) clearPreviewControlLongPress();
    });
  });
  control.addEventListener('contextmenu', event => {
    event.preventDefault();
    event.stopPropagation();
    clearPreviewControlLongPress();
    openControlRemoveMenu(control.dataset.layoutControl);
  });
  control.addEventListener('click', event => {
    event.preventDefault();
    event.stopPropagation();
    if (Date.now() < ignorePreviewControlClickUntil) return;
    hideControlRemoveMenu();
    selectPreviewControl(control.dataset.layoutControl);
  });
  control.addEventListener('keydown', event => {
    const controlId = control.dataset.layoutControl;
    if (!controlId) return;
    const step = event.shiftKey ? 8 : 1;
    const movement = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, -step],
      ArrowDown: [0, step]
    }[event.key];
    if (movement) {
      event.preventDefault();
      event.stopPropagation();
      nudgePreviewControl(controlId, movement[0], movement[1]);
      return;
    }
    if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      event.stopPropagation();
      removePreviewControl(controlId);
      return;
    }
    if (event.key === '+' || event.key === '=') {
      event.preventDefault();
      resizePreviewControlFromKeyboard(controlId, 0.05);
    } else if (event.key === '-' || event.key === '_') {
      event.preventDefault();
      resizePreviewControlFromKeyboard(controlId, -0.05);
    }
  });
}
function registerPreviewControlElement(controlId, element){
  if (!controlId || !element) return;
  previewControlElements.set(controlId, element);
  attachPreviewControlEvents(element);
}
function createExtraControlElement(controlId, type){
  if (!emulatorPreview) return null;
  const control = document.createElement('div');
  control.className = 'layout-control extra-control';
  if (type === 'quickLoad' || type === 'quickSave' || type === 'fastForward') {
    control.classList.add('menu-style-utility');
  }
  control.dataset.layoutControl = controlId;
  control.dataset.controlType = type;
  control.dataset.controlLabel = previewControlCatalog[type]?.label || 'Control';
  control.setAttribute('role', 'button');
  control.setAttribute('tabindex', '0');
  control.setAttribute('aria-label', `Move ${control.dataset.controlLabel}`);
  control.innerHTML = makeExtraControlMarkup(type);
  emulatorPreview.appendChild(control);
  control.dataset.baseWidth = String(control.offsetWidth || 56);
  control.dataset.baseHeight = String(control.offsetHeight || 56);
  registerPreviewControlElement(controlId, control);
  return control;
}
function defaultBaseControlState(controlId, orientation, element, previewRect){
  const baseWidth = Math.max(1, Number(element.dataset.baseWidth) || element.offsetWidth || 44);
  const baseHeight = Math.max(1, Number(element.dataset.baseHeight) || element.offsetHeight || 44);
  const scale = defaultBaseControllerScale(controlId, orientation);
  const scaledWidth = baseWidth * scale;
  const scaledHeight = baseHeight * scale;
  const width = Math.max(1, previewRect.width);
  const height = Math.max(1, previewRect.height);
  let left = (width - scaledWidth) / 2;
  let top = 10;
  if (orientation === 'portrait') {
    // My Boy!-style portrait zones: game screen at the top, shoulder/menu row
    // below it, Start/Select in the lower-middle, D-pad and A/B at the bottom.
    const side = Math.max(10, width * 0.03);
    const shoulderY = height * 0.64;
    const bottom = Math.max(14, height * 0.018);
    switch (controlId) {
      case 'shoulderLeft':
        left = side;
        top = shoulderY;
        break;
      case 'shoulderRight':
        left = width - scaledWidth - side;
        top = shoulderY;
        break;
      case 'menu':
        left = (width - scaledWidth) / 2;
        top = shoulderY - Math.max(0, (scaledHeight - 38) / 2);
        break;
      case 'dpad':
        left = side;
        top = height - scaledHeight - bottom;
        break;
      case 'ab':
        left = width - scaledWidth - side;
        top = height - scaledHeight - bottom;
        break;
      case 'startSelect':
        left = (width - scaledWidth) / 2;
        top = Math.min(height * 0.79, height - scaledHeight - Math.max(94, scaledHeight + 18));
        break;
    }
  } else {
    // Landscape defaults intentionally do not reuse any portrait geometry.
    const side = Math.max(18, width * 0.028);
    const topInset = Math.max(10, height * 0.026);
    const menuTop = Math.max(6, height * 0.014);
    const bottom = Math.max(12, height * 0.03);
    switch (controlId) {
      case 'shoulderLeft':
        left = side;
        top = topInset;
        break;
      case 'shoulderRight':
        left = width - scaledWidth - side;
        top = topInset;
        break;
      case 'menu':
        left = (width - scaledWidth) / 2;
        top = menuTop;
        break;
      case 'dpad':
        left = Math.max(18, width * 0.035);
        top = height - scaledHeight - bottom;
        break;
      case 'ab':
        left = width - scaledWidth - Math.max(18, width * 0.035);
        top = height - scaledHeight - bottom;
        break;
      case 'startSelect':
        left = (width - scaledWidth) / 2;
        top = height - scaledHeight - Math.max(10, height * 0.018);
        break;
    }
  }
  left = clampPreviewValue(left, 0, Math.max(0, width - scaledWidth));
  top = clampPreviewValue(top, 0, Math.max(0, height - scaledHeight));
  return {
    type: controlId,
    base: true,
    x: left / width,
    y: top / height,
    scale,
    label: element.dataset.controlLabel || previewControlCatalog[controlId]?.label || controlId
  };
}
function ensurePreviewLayoutInitialized(){
  const orientation = currentEditorOrientation();
  if (screenEditorOrientationSwitchInProgress) return false;
  if (previewLayoutInitialized && previewLayoutInitializedOrientation === orientation) return true;
  if (!emulatorPreview || !emulatorPreview.clientWidth || !emulatorPreview.clientHeight) return false;
  // IMPORTANT: never recycle the old orientation's live DOM/state here. A resize
  // event can fire before the dedicated orientation switch handler. If that old
  // state were measured against the new viewport, its positions would be clamped
  // and then accidentally saved into a different geometry. Only
  // syncScreenEditorOrientation() is allowed to tear down/swap orientations.
  if (previewLayoutInitialized && previewLayoutInitializedOrientation !== orientation) {
    return false;
  }
  let savedLayout = {};
  try {
    const candidate = JSON.parse(localStorage.getItem(currentPreviewLayoutStorageKey(orientation)) || '{}') || {};
    // Never accept a payload explicitly owned by the other orientation. This
    // protects against old/corrupted builds that wrote portrait data into the
    // landscape slot (or vice versa).
    if (!candidate.orientation || candidate.orientation === orientation) {
      savedLayout = candidate;
    }
  } catch (error) {
    savedLayout = {};
  }
  const previewRect = emulatorPreview.getBoundingClientRect();
  const baseControls = [...document.querySelectorAll('#screenSizePage [data-layout-control]')].filter(control => !control.classList.contains('extra-control'));
  baseControls.forEach(control => {
    const id = control.dataset.layoutControl;
    if (!id) return;
    const rect = control.getBoundingClientRect();
    control.dataset.baseWidth = String(control.offsetWidth || rect.width || 44);
    control.dataset.baseHeight = String(control.offsetHeight || rect.height || 44);
    // Never infer untouched defaults from the outgoing orientation's rendered
    // pixels. Each orientation gets its own deterministic normalized layout.
    const defaults = defaultBaseControlState(id, orientation, control, previewRect);
    previewLayoutDefaults[id] = defaults;
    const saved = savedLayout.controls?.[id] || {};
    previewLayoutState.controls[id] = {
      ...defaults,
      x: typeof saved.x === 'number' ? saved.x : defaults.x,
      y: typeof saved.y === 'number' ? saved.y : defaults.y,
      scale: typeof saved.scale === 'number' ? saved.scale : defaults.scale,
      hidden: saved.hidden === true
    };
    registerPreviewControlElement(id, control);
  });
  Object.entries(savedLayout.controls || {}).forEach(([controlId, saved]) => {
    if (saved?.base) return;
    const matched = String(controlId).match(/extra-(\d+)/);
    if (matched) previewExtraControlCounter = Math.max(previewExtraControlCounter, Number(matched[1]));
    const type = saved.type;
    if (!type || !previewControlCatalog[type] || saved.hidden === true) return;
    // v4.47 migration: old editor data could contain stacked copies of the
    // same control. Keep the first valid visible instance and drop the rest.
    if (isPreviewControlTypePlaced(type)) return;
    createExtraControlElement(controlId, type);
    previewLayoutState.controls[controlId] = {
      type,
      base: false,
      x: typeof saved.x === 'number' ? saved.x : 0.62,
      y: typeof saved.y === 'number' ? saved.y : 0.70,
      scale: typeof saved.scale === 'number' ? saved.scale : defaultControllerScale(orientation),
      label: previewControlCatalog[type].label,
      hidden: false
    };
  });
  const savedSelectedId = savedLayout.selectedId;
  if (savedSelectedId && previewLayoutState.controls[savedSelectedId] && !previewLayoutState.controls[savedSelectedId].hidden) {
    previewLayoutState.selectedId = savedSelectedId;
  } else if (savedSelectedId) {
    previewLayoutState.selectedId = null;
  }
  previewLayoutInitialized = true;
  previewLayoutInitializedOrientation = orientation;
  refreshAddControlAvailability();
  return true;
}
function serializePreviewLayoutState(){
  return JSON.stringify(previewLayoutState);
}
function buildPreviewLayoutPayload(orientation = previewLayoutInitializedOrientation || currentEditorOrientation()){
  let payload;
  try {
    payload = JSON.parse(serializePreviewLayoutState());
  } catch (_) {
    payload = { controls: {} };
  }
  payload.orientation = orientation;
  return payload;
}
function persistPreviewLayoutState(orientation = previewLayoutInitializedOrientation || currentEditorOrientation()){
  if (!previewLayoutInitialized) return;
  if (orientation !== 'portrait' && orientation !== 'landscape') return;
  // The live editor state belongs to exactly one orientation. Refuse any write
  // to a different key even if a late resize/orientation event asks for it.
  if (previewLayoutInitializedOrientation && orientation !== previewLayoutInitializedOrientation) return;
  // Never derive this key from the *new* device orientation during a rotation.
  // The initialized orientation owns this in-memory controller state until it
  // is discarded. This is what keeps portrait and landscape fully isolated.
  const payload = buildPreviewLayoutPayload(orientation);
  const json = JSON.stringify(payload);
  localStorage.setItem(currentPreviewLayoutStorageKey(orientation), json);
  if (window.AndroidBridge && typeof window.AndroidBridge.setControllerLayout === 'function') {
    nativeSetControllerLayout(json);
  }
  // Sync immediately so hidden base controls cannot be restored by a stale profile snapshot.
  if (typeof syncActiveLayoutProfileControllerFromEditor === 'function') {
    syncActiveLayoutProfileControllerFromEditor(orientation, payload);
  }
}
function commitScreenEditorState(){
  const orientation = previewLayoutInitializedOrientation || currentEditorOrientation();
  if (orientation !== 'portrait' && orientation !== 'landscape') return;
  // Capture the exact live orientation before leaving. No Save button is needed.
  if (previewLayoutInitialized) {
    const controllerPayload = buildPreviewLayoutPayload(orientation);
    const controllerJson = JSON.stringify(controllerPayload);
    const screenPayload = buildScreenLayoutPayload(orientation);
    const screenJson = JSON.stringify(screenPayload);
    localStorage.setItem(currentPreviewLayoutStorageKey(orientation), controllerJson);
    screenSizeState.orientation = orientation;
    localStorage.setItem(screenSizeStorageKey, JSON.stringify(screenSizeState));
    if (window.AndroidBridge && typeof window.AndroidBridge.commitGameplayLayout === 'function') {
      // Synchronous native transaction: Back -> Settings -> Start game always
      // reads the exact screen + controller layout that was just visible.
      nativeCommitGameplayLayout(controllerJson, screenJson);
      return;
    }
    if (window.AndroidBridge && typeof window.AndroidBridge.setControllerLayout === 'function') {
      nativeSetControllerLayout(controllerJson);
    }
    if (window.AndroidBridge && typeof window.AndroidBridge.setEmulatorScreenLayout === 'function') {
      nativeSetScreenLayout(screenJson);
    }
    return;
  }
  // Screen-only fallback if the editor has not finished measuring its controls.
  persistScreenSizeState(orientation);
}
function adaptiveControlScaleHandlePlacement(controlRect, previewRect, handleSize, forcedXDirection = 0, forcedYDirection = 0){
  const freeLeft = Math.max(0, controlRect.left - previewRect.left);
  const freeRight = Math.max(0, previewRect.right - controlRect.right);
  const freeTop = Math.max(0, controlRect.top - previewRect.top);
  const freeBottom = Math.max(0, previewRect.bottom - controlRect.bottom);
  // Put the resize handle on the sides with the most free canvas. This makes
  // edge controls resizeable instead of pinning the handle against/off-screen.
  const preferredXDirection = freeRight >= freeLeft ? 1 : -1;
  const preferredYDirection = freeBottom >= freeTop ? 1 : -1;
  const previousXDirection = Number(previewScaleHandle?.dataset.scaleDirectionX) || 0;
  const previousYDirection = Number(previewScaleHandle?.dataset.scaleDirectionY) || 0;
  const xDirection = forcedXDirection || (
    previousXDirection && Math.abs(freeRight - freeLeft) < 18 ? previousXDirection : preferredXDirection
  );
  const yDirection = forcedYDirection || (
    previousYDirection && Math.abs(freeBottom - freeTop) < 18 ? previousYDirection : preferredYDirection
  );
  const overlap = 0.18; // Keep most of the handle outside the draggable body.
  const desiredLeft = xDirection > 0
    ? controlRect.right - previewRect.left - handleSize * overlap
    : controlRect.left - previewRect.left - handleSize * (1 - overlap);
  const desiredTop = yDirection > 0
    ? controlRect.bottom - previewRect.top - handleSize * overlap
    : controlRect.top - previewRect.top - handleSize * (1 - overlap);
  return {
    left: clampPreviewValue(desiredLeft, 0, Math.max(0, previewRect.width - handleSize)),
    top: clampPreviewValue(desiredTop, 0, Math.max(0, previewRect.height - handleSize)),
    xDirection,
    yDirection,
    corner: `${yDirection > 0 ? 'bottom' : 'top'}-${xDirection > 0 ? 'right' : 'left'}`
  };
}
function updateScaleHandle(){
  if (!previewScaleHandle || !emulatorPreview) return;
  const previewRect = emulatorPreview.getBoundingClientRect();
  // The emulator screen uses the exact same expand/minimize handle as the
  // controller buttons. Tapping the screen selects it, then dragging this
  // handle outward grows it and dragging inward shrinks it.
  if (screenFrameSelected && previewScreenFrame) {
    const frameRect = previewScreenFrame.getBoundingClientRect();
    const handleSize = previewScaleHandle.offsetWidth || 42;
    const scalingScreen = Boolean(activeScreenScale);
    const placement = adaptiveControlScaleHandlePlacement(
      frameRect,
      previewRect,
      handleSize,
      scalingScreen ? activeScreenScale.xDirection : 0,
      scalingScreen ? activeScreenScale.yDirection : 0
    );
    previewScaleHandle.style.left = `${placement.left}px`;
    previewScaleHandle.style.top = `${placement.top}px`;
    previewScaleHandle.dataset.scaleDirectionX = String(placement.xDirection);
    previewScaleHandle.dataset.scaleDirectionY = String(placement.yDirection);
    previewScaleHandle.dataset.scaleCorner = placement.corner;
    previewScaleHandle.style.cursor = placement.xDirection === placement.yDirection ? 'nwse-resize' : 'nesw-resize';
    previewScaleHandle.classList.add('visible', 'screen-scale-mode');
    previewScaleHandle.setAttribute('aria-label', 'Resize emulator screen');
    if (previewScaleValue) {
      const best = fitGbaScreen(previewRect.width * 0.82, previewRect.height * 0.88, 3 / 2);
      const percent = Math.round(clampPreviewValue(frameRect.width / Math.max(1, best.width), 0.2, 3) * 100);
      previewScaleValue.textContent = `${percent}%`;
      previewScaleValue.setAttribute('aria-label', `Emulator screen size ${percent}%`);
      const badgeWidth = previewScaleValue.offsetWidth || 52;
      const badgeHeight = previewScaleValue.offsetHeight || 28;
      let badgeLeft = frameRect.left - previewRect.left + (frameRect.width - badgeWidth) / 2;
      let badgeTop = frameRect.bottom - previewRect.top + 7;
      if (badgeTop + badgeHeight > previewRect.height - 6) {
        badgeTop = frameRect.top - previewRect.top - badgeHeight - 7;
      }
      badgeLeft = clampPreviewValue(badgeLeft, 6, Math.max(6, previewRect.width - badgeWidth - 6));
      badgeTop = clampPreviewValue(badgeTop, 6, Math.max(6, previewRect.height - badgeHeight - 6));
      previewScaleValue.style.left = `${badgeLeft}px`;
      previewScaleValue.style.top = `${badgeTop}px`;
      previewScaleValue.classList.add('visible');
    }
    return;
  }
  previewScaleHandle.classList.remove('screen-scale-mode');
  previewScaleHandle.setAttribute('aria-label', 'Resize selected control');
  if (!previewLayoutState.selectedId) {
    previewScaleHandle.classList.remove('visible');
    previewScaleValue?.classList.remove('visible');
    return;
  }
  const selected = previewControlElements.get(previewLayoutState.selectedId);
  const state = previewLayoutState.controls[previewLayoutState.selectedId];
  if (!selected || !state || state.hidden) {
    previewScaleHandle.classList.remove('visible');
    previewScaleValue?.classList.remove('visible');
    return;
  }
  const controlRect = selected.getBoundingClientRect();
  const handleSize = previewScaleHandle.offsetWidth || 42;
  const scalingThisControl = activePreviewScale?.controlId === previewLayoutState.selectedId;
  const placement = adaptiveControlScaleHandlePlacement(
    controlRect,
    previewRect,
    handleSize,
    scalingThisControl ? activePreviewScale.xDirection : 0,
    scalingThisControl ? activePreviewScale.yDirection : 0
  );
  previewScaleHandle.style.left = `${placement.left}px`;
  previewScaleHandle.style.top = `${placement.top}px`;
  previewScaleHandle.dataset.scaleDirectionX = String(placement.xDirection);
  previewScaleHandle.dataset.scaleDirectionY = String(placement.yDirection);
  previewScaleHandle.dataset.scaleCorner = placement.corner;
  previewScaleHandle.style.cursor = placement.xDirection === placement.yDirection ? 'nwse-resize' : 'nesw-resize';
  previewScaleHandle.classList.add('visible');
  if (previewScaleValue) {
    const percent = Math.round(clampPreviewValue(Number(state.scale) || 1, 0.65, 1.7) * 100);
    previewScaleValue.textContent = `${percent}%`;
    previewScaleValue.setAttribute('aria-label', `Selected control size ${percent}%`);
    const badgeWidth = previewScaleValue.offsetWidth || 52;
    const badgeHeight = previewScaleValue.offsetHeight || 28;
    let badgeLeft = controlRect.left - previewRect.left + (controlRect.width - badgeWidth) / 2;
    let badgeTop = controlRect.bottom - previewRect.top + 7;
    // If there is not enough space below the button, put the value above it.
    if (badgeTop + badgeHeight > previewRect.height - 6) {
      badgeTop = controlRect.top - previewRect.top - badgeHeight - 7;
    }
    badgeLeft = clampPreviewValue(badgeLeft, 6, Math.max(6, previewRect.width - badgeWidth - 6));
    badgeTop = clampPreviewValue(badgeTop, 6, Math.max(6, previewRect.height - badgeHeight - 6));
    previewScaleValue.style.left = `${badgeLeft}px`;
    previewScaleValue.style.top = `${badgeTop}px`;
    previewScaleValue.classList.add('visible');
  }
  if (controlRemoveMenu?.classList.contains('open') && controlRemoveMenu.dataset.controlId === previewLayoutState.selectedId) {
    positionControlRemoveMenu(previewLayoutState.selectedId);
  }
}
function clearPreviewSelection(){
  previewLayoutState.selectedId = null;
  previewControlElements.forEach(element => {
    element.classList.remove('is-selected');
  });
  previewScaleHandle?.classList.remove('visible');
  previewScaleValue?.classList.remove('visible');
  hideControlRemoveMenu();
  persistPreviewLayoutState();
}
function selectPreviewControl(controlId){
  if (!ensurePreviewLayoutInitialized()) return;
  clearScreenFrameSelection();
  if (!previewLayoutState.controls[controlId] || previewLayoutState.controls[controlId].hidden) return;
  previewLayoutState.selectedId = controlId;
  previewControlElements.forEach((element, id) => {
    element.classList.toggle('is-selected', id === controlId);
  });
  updateScaleHandle();
  persistPreviewLayoutState();
}
function applyPreviewLayoutControls(){
  if (screenEditorOrientationSwitchInProgress) return;
  if (previewLayoutInitialized && previewLayoutInitializedOrientation !== currentEditorOrientation()) return;
  if (!ensurePreviewLayoutInitialized() || !emulatorPreview) return;
  const previewWidth = emulatorPreview.clientWidth;
  const previewHeight = emulatorPreview.clientHeight;
  if (!previewWidth || !previewHeight) return;
  Object.entries(previewLayoutState.controls).forEach(([controlId, state]) => {
    const element = previewControlElements.get(controlId);
    if (!element) return;
    element.classList.toggle('is-hidden', state.hidden === true);
    if (state.hidden) return;
    const scale = clampPreviewValue(Number(state.scale) || 1, 0.65, 1.7);
    const baseWidth = Number(element.dataset.baseWidth) || element.offsetWidth || 44;
    const baseHeight = Number(element.dataset.baseHeight) || element.offsetHeight || 44;
    const scaledWidth = baseWidth * scale;
    const scaledHeight = baseHeight * scale;
    const left = clampPreviewValue(
      (typeof state.x === 'number' ? state.x : 0.5) * previewWidth,
      0,
      Math.max(0, previewWidth - scaledWidth)
    );
    const top = clampPreviewValue(
      (typeof state.y === 'number' ? state.y : 0.5) * previewHeight,
      0,
      Math.max(0, previewHeight - scaledHeight)
    );
    // Keep the saved state orientation-local. These values are only updated by
    // explicit user interaction in this orientation; rendering must not convert
    // one orientation into another. Inline !important makes the user's saved
    // coordinates authoritative over the CSS default portrait/landscape positions.
    state.scale = scale;
    element.style.setProperty('left', `${left}px`, 'important');
    element.style.setProperty('top', `${top}px`, 'important');
    element.style.setProperty('right', 'auto', 'important');
    element.style.setProperty('bottom', 'auto', 'important');
    element.style.setProperty('transform', `scale(${scale})`, 'important');
  });
  if (previewLayoutState.selectedId && previewLayoutState.controls[previewLayoutState.selectedId] && !previewLayoutState.controls[previewLayoutState.selectedId].hidden) {
    // Repaint only; persistence on every pointermove can stall WebView dragging.
    previewControlElements.forEach((element, id) => {
      element.classList.toggle('is-selected', id === previewLayoutState.selectedId);
    });
    updateScaleHandle();
  } else {
    if (previewLayoutState.selectedId && previewLayoutState.controls[previewLayoutState.selectedId]?.hidden) {
      previewLayoutState.selectedId = null;
    }
    updateScaleHandle();
  }
}
let activePreviewDrag = null;
let activePreviewScale = null;
function stopPreviewDrag(event){
  if (!activePreviewDrag) return;
  if (event?.pointerId !== undefined && activePreviewDrag.pointerId !== undefined && event.pointerId !== activePreviewDrag.pointerId) return;
  flushPendingPreviewDrag();
  const drag = activePreviewDrag;
  const state = previewLayoutState.controls[drag.controlId];
  // Commit compositor-only movement once after the gesture.
  if (state && drag.element) {
    const finalLeft = Number.isFinite(drag.lastLeft) ? drag.lastLeft : drag.startLeft;
    const finalTop = Number.isFinite(drag.lastTop) ? drag.lastTop : drag.startTop;
    state.x = finalLeft / Math.max(1, drag.previewWidth);
    state.y = finalTop / Math.max(1, drag.previewHeight);
    drag.element.style.setProperty('left', `${finalLeft}px`, 'important');
    drag.element.style.setProperty('top', `${finalTop}px`, 'important');
    drag.element.style.setProperty('right', 'auto', 'important'); drag.element.style.setProperty('bottom', 'auto', 'important');
    drag.element.style.setProperty('transform', `scale(${drag.scale})`, 'important');
  }
  activePreviewDrag = null;
  drag.element?.classList.remove('is-dragging');
  emulatorPreview?.classList.remove('editor-drag-active');
  try { if (drag.element?.hasPointerCapture?.(drag.pointerId)) drag.element.releasePointerCapture(drag.pointerId); } catch (_) {}
  window.removeEventListener('pointermove', handlePreviewDragMove);
  window.removeEventListener('pointerup', stopPreviewDrag);
  window.removeEventListener('pointercancel', stopPreviewDrag);
  updateScaleHandle();
  persistPreviewLayoutState();
}
function handlePreviewDragMove(event){
  if (!activePreviewDrag || !emulatorPreview) return;
  const sample = latestPointerSample(event);
  if (activePreviewDrag.pointerId !== undefined && sample.pointerId !== undefined && sample.pointerId !== activePreviewDrag.pointerId) return;
  pendingPreviewDragSample = sample;
  if (previewDragAnimationFrame) return;
  previewDragAnimationFrame = requestAnimationFrame(() => {
    previewDragAnimationFrame = 0;
    const next = pendingPreviewDragSample;
    pendingPreviewDragSample = null;
    if (next) applyPreviewDragMove(next);
  });
}
function applyPreviewDragMove(event){
  const drag = activePreviewDrag;
  if (!drag || !emulatorPreview) return;
  if (drag.pointerId !== undefined && event.pointerId !== undefined && event.pointerId !== drag.pointerId) return;
  if (previewControlLongPressState?.controlId === drag.controlId) {
    const moveX = event.clientX - previewControlLongPressState.startX;
    const moveY = event.clientY - previewControlLongPressState.startY;
    if (Math.hypot(moveX, moveY) > 8) clearPreviewControlLongPress();
  }
  const state = previewLayoutState.controls[drag.controlId];
  const element = drag.element || previewControlElements.get(drag.controlId);
  if (!state || !element) return;
  const nextLeft = clampPreviewValue(
    drag.startLeft + (event.clientX - drag.startX),
    0,
    Math.max(0, drag.previewWidth - drag.scaledWidth)
  );
  const nextTop = clampPreviewValue(
    drag.startTop + (event.clientY - drag.startY),
    0,
    Math.max(0, drag.previewHeight - drag.scaledHeight)
  );
  drag.lastLeft = nextLeft;
  drag.lastTop = nextTop;
  // Compositor-only hot path; normalized X/Y is committed on pointerup.
  const translateX = nextLeft - drag.startLeft;
  const translateY = nextTop - drag.startTop;
  element.style.setProperty('transform',
    `translate3d(${translateX}px, ${translateY}px, 0) scale(${drag.scale})`, 'important');
}
function beginPreviewControlDrag(controlId, event){
  if (!ensurePreviewLayoutInitialized() || !emulatorPreview) return;
  const state = previewLayoutState.controls[controlId];
  const element = previewControlElements.get(controlId);
  if (!state || !element) return;
  selectPreviewControl(controlId);
  const previewRect = emulatorPreview.getBoundingClientRect();
  const elementRect = element.getBoundingClientRect();
  const scale = clampPreviewValue(Number(state.scale) || 1, 0.65, 1.7);
  const baseWidth = Number(element.dataset.baseWidth) || element.offsetWidth || 44;
  const baseHeight = Number(element.dataset.baseHeight) || element.offsetHeight || 44;
  const startLeft = elementRect.left - previewRect.left;
  const startTop = elementRect.top - previewRect.top;
  activePreviewDrag = {
    controlId,
    element,
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    // Use the exact rendered position so drag never jumps at gesture start.
    startLeft,
    startTop,
    lastLeft: startLeft,
    lastTop: startTop,
    previewWidth: previewRect.width,
    previewHeight: previewRect.height,
    scaledWidth: baseWidth * scale,
    scaledHeight: baseHeight * scale,
    scale
  };
  // Hide geometry-heavy affordances until release.
  element.classList.add('is-dragging');
  emulatorPreview.classList.add('editor-drag-active');
  previewScaleHandle?.classList.remove('visible');
  previewScaleValue?.classList.remove('visible');
  // Keep drag ownership when the finger leaves the control.
  try { element.setPointerCapture?.(event.pointerId); } catch (_) {}
  window.addEventListener('pointermove', handlePreviewDragMove);
  window.addEventListener('pointerup', stopPreviewDrag);
  window.addEventListener('pointercancel', stopPreviewDrag);
}
function stopPreviewScale(event){
  if (!activePreviewScale) return;
  if (event?.pointerId !== undefined && activePreviewScale.pointerId !== undefined && event.pointerId !== activePreviewScale.pointerId) return;
  flushPendingPreviewScale();
  const scale = activePreviewScale;
  activePreviewScale = null;
  try { if (previewScaleHandle?.hasPointerCapture?.(scale.pointerId)) previewScaleHandle.releasePointerCapture(scale.pointerId); } catch (_) {}
  window.removeEventListener('pointermove', handlePreviewScaleMove);
  window.removeEventListener('pointerup', stopPreviewScale);
  window.removeEventListener('pointercancel', stopPreviewScale);
  updateScaleHandle();
  persistPreviewLayoutState();
}
function handlePreviewScaleMove(event){
  if (!activePreviewScale || !emulatorPreview) return;
  const sample = latestPointerSample(event);
  if (activePreviewScale.pointerId !== undefined && sample.pointerId !== undefined && sample.pointerId !== activePreviewScale.pointerId) return;
  pendingPreviewScaleSample = sample;
  if (previewScaleAnimationFrame) return;
  previewScaleAnimationFrame = requestAnimationFrame(() => {
    previewScaleAnimationFrame = 0;
    const next = pendingPreviewScaleSample;
    pendingPreviewScaleSample = null;
    if (next) applyPreviewScaleMove(next);
  });
}
function applyPreviewScaleMove(event){
  if (!activePreviewScale || !emulatorPreview) return;
  if (activePreviewScale.pointerId !== undefined && event.pointerId !== undefined && event.pointerId !== activePreviewScale.pointerId) return;
  const state = previewLayoutState.controls[activePreviewScale.controlId];
  if (!state) return;
  const deltaX = event.clientX - activePreviewScale.startX;
  const deltaY = event.clientY - activePreviewScale.startY;
  // Whichever corner the handle moved to, dragging away from the selected
  // controller increases its size and dragging back toward it decreases it.
  const delta = (
    deltaX * activePreviewScale.xDirection +
    deltaY * activePreviewScale.yDirection
  ) / 180;
  const nextScale = clampPreviewValue(activePreviewScale.startScale + delta, 0.65, 1.7);
  const scaledWidth = activePreviewScale.baseWidth * nextScale;
  const scaledHeight = activePreviewScale.baseHeight * nextScale;
  let nextLeft = activePreviewScale.startLeft;
  let nextTop = activePreviewScale.startTop;
  // When the adaptive handle is on the left/top, keep the opposite edge
  // anchored so the control grows toward the dragged handle naturally.
  if (activePreviewScale.xDirection < 0) {
    nextLeft += activePreviewScale.startWidth - scaledWidth;
  }
  if (activePreviewScale.yDirection < 0) {
    nextTop += activePreviewScale.startHeight - scaledHeight;
  }
  nextLeft = clampPreviewValue(nextLeft, 0, Math.max(0, activePreviewScale.previewWidth - scaledWidth));
  nextTop = clampPreviewValue(nextTop, 0, Math.max(0, activePreviewScale.previewHeight - scaledHeight));
  state.scale = nextScale;
  state.x = nextLeft / Math.max(1, activePreviewScale.previewWidth);
  state.y = nextTop / Math.max(1, activePreviewScale.previewHeight);
  const element = activePreviewScale.element || previewControlElements.get(activePreviewScale.controlId);
  if (element) {
    element.style.setProperty('left', `${nextLeft}px`, 'important');
    element.style.setProperty('top', `${nextTop}px`, 'important');
    element.style.setProperty('right', 'auto', 'important');
    element.style.setProperty('bottom', 'auto', 'important');
    element.style.setProperty('transform', `scale(${nextScale})`, 'important');
  }
  updateScaleHandle();
}
function beginPreviewScale(event){
  if (!ensurePreviewLayoutInitialized() || !emulatorPreview) return;
  const controlId = previewLayoutState.selectedId;
  const state = previewLayoutState.controls[controlId];
  const element = previewControlElements.get(controlId);
  if (!controlId || !state || !element) return;
  event.preventDefault();
  event.stopPropagation();
  hideControlRemoveMenu();
  const previewRect = emulatorPreview.getBoundingClientRect();
  const elementRect = element.getBoundingClientRect();
  const startScale = clampPreviewValue(Number(state.scale) || 1, 0.65, 1.7);
  const baseWidth = Number(element.dataset.baseWidth) || element.offsetWidth || 44;
  const baseHeight = Number(element.dataset.baseHeight) || element.offsetHeight || 44;
  const startWidth = baseWidth * startScale;
  const startHeight = baseHeight * startScale;
  const xDirection = Number(previewScaleHandle?.dataset.scaleDirectionX) || 1;
  const yDirection = Number(previewScaleHandle?.dataset.scaleDirectionY) || 1;
  activePreviewScale = {
    controlId,
    element,
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    startScale,
    xDirection,
    yDirection,
    baseWidth,
    baseHeight,
    startWidth,
    startHeight,
    startLeft: elementRect.left - previewRect.left,
    startTop: elementRect.top - previewRect.top,
    previewWidth: previewRect.width,
    previewHeight: previewRect.height
  };
  // Resize capture stays separate from controller-body drag capture.
  try { previewScaleHandle?.setPointerCapture?.(event.pointerId); } catch (_) {}
  window.addEventListener('pointermove', handlePreviewScaleMove);
  window.addEventListener('pointerup', stopPreviewScale);
  window.addEventListener('pointercancel', stopPreviewScale);
}
function removeExtraPreviewControls(){
  Object.entries(previewLayoutState.controls).forEach(([controlId, state]) => {
    if (state.base) return;
    previewControlElements.get(controlId)?.remove();
    previewControlElements.delete(controlId);
    delete previewLayoutState.controls[controlId];
  });
}
function resetPreviewLayout(){
  if (!ensurePreviewLayoutInitialized()) return;
  removeExtraPreviewControls();
  Object.entries(previewLayoutDefaults).forEach(([controlId, defaults]) => {
    previewLayoutState.controls[controlId] = { ...defaults };
  });
  previewLayoutState.selectedId = 'dpad';
  previewExtraControlCounter = 0;
  applyPreviewLayoutControls();
  persistPreviewLayoutState();
  renderRestoreDefaultControlsSection();
  refreshAddControlAvailability();
}
function getNewPreviewControlPosition(){
  const extraCount = Object.values(previewLayoutState.controls).filter(control => !control.base).length;
  const offsetX = (extraCount % 3) * 0.06;
  const offsetY = Math.floor(extraCount / 3) * 0.06;
  return {
    x: clampPreviewValue(0.60 + offsetX, 0.12, 0.84),
    y: clampPreviewValue(0.56 + offsetY, 0.12, 0.80)
  };
}
function addPreviewControl(type){
  if (!ensurePreviewLayoutInitialized() || !previewControlCatalog[type]) return;
  if (isPreviewControlTypePlaced(type)) {
    refreshAddControlAvailability();
    showToast(`${previewControlCatalog[type].label} is already on this screen`);
    return;
  }
  const controlId = `extra-${++previewExtraControlCounter}`;
  createExtraControlElement(controlId, type);
  const position = getNewPreviewControlPosition();
  previewLayoutState.controls[controlId] = {
    type,
    base: false,
    x: position.x,
    y: position.y,
    scale: defaultControllerScale(previewLayoutInitializedOrientation || currentEditorOrientation()),
    label: previewControlCatalog[type].label,
    hidden: false
  };
  applyPreviewLayoutControls();
  selectPreviewControl(controlId);
  persistPreviewLayoutState();
  refreshAddControlAvailability();
  closeAddControlModal();
  showToast(`${previewControlCatalog[type].label} added`);
}
function openAddControlModal(){
  hideControlRemoveMenu();
  renderRestoreDefaultControlsSection();
  refreshAddControlAvailability();
  addControlModal?.classList.add('open');
  addControlModal?.setAttribute('aria-hidden', 'false');
  previewScaleHandle?.classList.remove('visible');
  previewScaleValue?.classList.remove('visible');
}
function closeAddControlModal(){
  addControlModal?.classList.remove('open');
  addControlModal?.setAttribute('aria-hidden', 'true');
  updateScaleHandle();
}
let screenFrameSelected = false;
function clearScreenFrameSelection(){
  screenFrameSelected = false;
  previewScreenFrame?.classList.remove('is-selected');
  if (!previewLayoutState.selectedId) {
    previewScaleHandle?.classList.remove('visible', 'screen-scale-mode');
    previewScaleValue?.classList.remove('visible');
  }
}
function selectScreenFrame(){
  screenFrameSelected = true;
  previewScreenFrame?.classList.add('is-selected');
  clearPreviewSelection();
  updateScaleHandle();
}
function enterCustomScreenGestureMode(){
  setCurrentScreenMode('custom');
  if (screenPreviewShell) screenPreviewShell.dataset.sizeMode = 'custom';
  screenSizeModeButtons.forEach(button => button.classList.remove('active'));
  updateScreenSizeSummary();
}
function applyScreenSizePreview(){
  if (!screenPreviewShell) return;
  const orientation = currentEditorOrientation();
  const mode = currentScreenMode();
  screenSizeState.orientation = orientation;
  screenPreviewShell.dataset.orientation = orientation;
  screenPreviewShell.dataset.sizeMode = mode;
  previewScreenFrame?.classList.toggle('is-selected', screenFrameSelected);
  screenPreviewShell.classList.toggle('portrait', orientation === 'portrait');
  screenPreviewShell.classList.toggle('landscape', orientation === 'landscape');
  document.body.classList.toggle('screen-editor-portrait', orientation === 'portrait');
  document.body.classList.toggle('screen-editor-landscape', orientation === 'landscape');
  screenSizeModeButtons.forEach(button => {
    button.classList.toggle('active', button.dataset.sizeMode === mode);
  });
  updateScreenSizeSummary();
  requestAnimationFrame(() => {
    updateResponsiveScreenFrame();
    applyPreviewLayoutControls();
  });
}
screenSizeModeButtons.forEach(button => {
  button.addEventListener('click', () => {
    setCurrentScreenMode(button.dataset.sizeMode);
    setCurrentCustomFrame(null);
    clearScreenFrameSelection();
    persistScreenSizeState();
    applyScreenSizePreview();
    screenContextMenu?.classList.remove('open');
    showToast(`${screenSizeLabels[button.dataset.sizeMode] || 'Screen size mode'} selected`);
  });
});
let lastScreenEditorOrientation = null;
function syncScreenEditorOrientation(){
  if (currentOrientationPage !== 'screenSizePage') return;
  const next = currentEditorOrientation();
  if (next === lastScreenEditorOrientation &&
      previewLayoutInitializedOrientation === next &&
      !screenEditorOrientationSwitchInProgress) {
    updateShellOrientation(currentOrientationPage);
    applyScreenSizePreview();
    return;
  }
  const previous = previewLayoutInitializedOrientation || lastScreenEditorOrientation;
  // Rotation is a dataset swap, not an edit. Never rewrite the outgoing
  // orientation merely because the viewport changed. User edits are already
  // saved by drag/resize/add/remove actions. If the phone rotates in the middle
  // of an active gesture, preserve only that explicit in-progress edit.
  if (previewLayoutInitialized && previous && previous !== next &&
      (activePreviewDrag || activePreviewScale || activeScreenScale || activeScreenDrag || activeScreenEdgeResize)) {
    persistPreviewLayoutState(previous);
    persistScreenSizeState(previous);
  }
  screenEditorOrientationSwitchInProgress = true;
  const generation = ++screenEditorOrientationGeneration;
  lastScreenEditorOrientation = next;
  // Native SharedPreferences are the gameplay source of truth. Load the exact
  // incoming orientation before rebuilding its editor DOM.
  hydrateEditorOrientationFromNative(next);
  // Drop the old orientation's DOM-only geometry. The saved data is already in
  // its own orientation key. Base controls must have their old inline !important
  // positions removed so the NEW orientation's CSS defaults can be measured.
  activePreviewDrag = null;
  activePreviewScale = null;
  activeScreenScale = null;
  if (activeScreenDrag) {
    window.removeEventListener('pointermove', handleScreenDragMove);
    window.removeEventListener('pointerup', stopScreenDrag);
    window.removeEventListener('pointercancel', stopScreenDrag);
  }
  activeScreenDrag = null;
  pendingScreenDragSample = null;
  if (screenDragAnimationFrame) {
    cancelAnimationFrame(screenDragAnimationFrame);
    screenDragAnimationFrame = 0;
  }
  if (activeScreenEdgeResize) {
    window.removeEventListener('pointermove', handleScreenEdgeResizeMove);
    window.removeEventListener('pointerup', stopScreenEdgeResize);
    window.removeEventListener('pointercancel', stopScreenEdgeResize);
  }
  activeScreenEdgeResize = null;
  previewLayoutInitialized = false;
  previewLayoutInitializedOrientation = null;
  emulatorPreview?.querySelectorAll('.extra-control').forEach(control => control.remove());
  previewControlElements.clear();
  Object.keys(previewLayoutDefaults).forEach(key => delete previewLayoutDefaults[key]);
  previewLayoutState.controls = {};
  previewLayoutState.selectedId = 'dpad';
  previewExtraControlCounter = 0;
  screenFrameSelected = false;
  clearBasePreviewControlInlineGeometry();
  hideControlRemoveMenu();
  updateShellOrientation(currentOrientationPage);
  screenPreviewShell?.classList.toggle('portrait', next === 'portrait');
  screenPreviewShell?.classList.toggle('landscape', next === 'landscape');
  document.body.classList.toggle('screen-editor-portrait', next === 'portrait');
  document.body.classList.toggle('screen-editor-landscape', next === 'landscape');
  // Wait for two paint/layout passes. This prevents measuring the new controls
  // while Android/WebView is still reporting dimensions from the old rotation.
  requestAnimationFrame(() => requestAnimationFrame(() => {
    if (generation !== screenEditorOrientationGeneration) return;
    if (currentEditorOrientation() !== next) {
      screenEditorOrientationSwitchInProgress = false;
      syncScreenEditorOrientation();
      return;
    }
    screenEditorOrientationSwitchInProgress = false;
    applyScreenSizePreview();
    applyScreenEditorGrid();
    ensurePreviewLayoutInitialized();
    applyPreviewLayoutControls();
  }));
}
let screenEditorOrientationSyncTimer = null;
function scheduleScreenEditorOrientationSync(delay = 180){
  clearTimeout(screenEditorOrientationSyncTimer);
  screenEditorOrientationSyncTimer = window.setTimeout(() => {
    screenEditorOrientationSyncTimer = null;
    syncScreenEditorOrientation();
  }, delay);
}
// Android/WebView can emit several resize passes during one physical rotation.
// Wait until the viewport settles, then switch to the other saved dataset once.
window.addEventListener('resize', () => scheduleScreenEditorOrientationSync(180));
window.addEventListener('orientationchange', () => scheduleScreenEditorOrientationSync(220));
if (window.visualViewport) {
  window.visualViewport.addEventListener('resize', () => scheduleScreenEditorOrientationSync(180));
}
let activeScreenEdgeResize = null;
let pendingScreenEdgeResizeSample = null;
let screenEdgeResizeAnimationFrame = 0;
function hitTestScreenResizeEdge(event){
  if (!previewScreenFrame) return null;
  const rect = previewScreenFrame.getBoundingClientRect();
  const threshold = Math.max(16, Math.min(28, Math.min(rect.width, rect.height) * 0.07));
  const distances = [
    ['left', Math.abs(event.clientX - rect.left)],
    ['right', Math.abs(event.clientX - rect.right)],
    ['top', Math.abs(event.clientY - rect.top)],
    ['bottom', Math.abs(event.clientY - rect.bottom)]
  ].filter(([, distance]) => distance <= threshold);
  if (!distances.length) return null;
  distances.sort((a, b) => a[1] - b[1]);
  return distances[0][0];
}
function beginScreenEdgeResize(event, edge){
  if (!edge || !emulatorPreview || !previewScreenFrame) return false;
  const previewRect = emulatorPreview.getBoundingClientRect();
  const frameRect = previewScreenFrame.getBoundingClientRect();
  activeScreenEdgeResize = {
    orientation: currentEditorOrientation(),
    edge,
    startX: event.clientX,
    startY: event.clientY,
    left: frameRect.left - previewRect.left,
    top: frameRect.top - previewRect.top,
    width: frameRect.width,
    height: frameRect.height,
    preserveAspect: currentScreenMode() !== 'stretch'
  };
  clearPreviewLongPress();
  hideControlRemoveMenu();
  markPreviewReleaseClickIgnored(520);
  selectScreenFrame();
  event.preventDefault();
  event.stopPropagation();
  window.addEventListener('pointermove', handleScreenEdgeResizeMove);
  window.addEventListener('pointerup', stopScreenEdgeResize);
  window.addEventListener('pointercancel', stopScreenEdgeResize);
  return true;
}
function applyScreenEdgeResizeMove(event){
  const state = activeScreenEdgeResize;
  if (!state || !emulatorPreview) return;
  const previewRect = emulatorPreview.getBoundingClientRect();
  const maxW = previewRect.width;
  const maxH = previewRect.height;
  const minW = Math.max(96, maxW * 0.20);
  const minH = Math.max(72, maxH * 0.16);
  const aspect = 3 / 2;
  const dx = event.clientX - state.startX;
  const dy = event.clientY - state.startY;
  let left = state.left;
  let top = state.top;
  let width = state.width;
  let height = state.height;
  if (!state.preserveAspect) {
    if (state.edge === 'left') {
      const right = state.left + state.width;
      left = clampPreviewValue(state.left + dx, 0, right - minW);
      width = right - left;
    } else if (state.edge === 'right') {
      width = clampPreviewValue(state.width + dx, minW, maxW - state.left);
    } else if (state.edge === 'top') {
      const bottom = state.top + state.height;
      top = clampPreviewValue(state.top + dy, 0, bottom - minH);
      height = bottom - top;
    } else if (state.edge === 'bottom') {
      height = clampPreviewValue(state.height + dy, minH, maxH - state.top);
    }
  } else if (state.edge === 'left' || state.edge === 'right') {
    const anchoredRight = state.left + state.width;
    let requestedWidth = state.edge === 'left' ? state.width - dx : state.width + dx;
    const centerY = state.top + state.height / 2;
    const maxHeightFromCenter = 2 * Math.min(centerY, maxH - centerY);
    const maxWidthByHeight = Math.max(minW, maxHeightFromCenter * aspect);
    const maxWidthByHorizontal = state.edge === 'left' ? anchoredRight : maxW - state.left;
    width = clampPreviewValue(requestedWidth, Math.max(minW, minH * aspect), Math.min(maxWidthByHeight, maxWidthByHorizontal));
    height = width / aspect;
    left = state.edge === 'left' ? anchoredRight - width : state.left;
    top = clampPreviewValue(centerY - height / 2, 0, maxH - height);
  } else {
    const anchoredBottom = state.top + state.height;
    let requestedHeight = state.edge === 'top' ? state.height - dy : state.height + dy;
    const centerX = state.left + state.width / 2;
    const maxWidthFromCenter = 2 * Math.min(centerX, maxW - centerX);
    const maxHeightByWidth = Math.max(minH, maxWidthFromCenter / aspect);
    const maxHeightByVertical = state.edge === 'top' ? anchoredBottom : maxH - state.top;
    height = clampPreviewValue(requestedHeight, Math.max(minH, minW / aspect), Math.min(maxHeightByWidth, maxHeightByVertical));
    width = height * aspect;
    top = state.edge === 'top' ? anchoredBottom - height : state.top;
    left = clampPreviewValue(centerX - width / 2, 0, maxW - width);
  }
  enterCustomScreenGestureMode();
  setCurrentCustomFrame({
    left: left / maxW,
    top: top / maxH,
    width: width / maxW,
    height: height / maxH
  });
  updateResponsiveScreenFrame();
  updateScaleHandle();
}
function handleScreenEdgeResizeMove(event){
  if (!activeScreenEdgeResize) return;
  pendingScreenEdgeResizeSample = latestPointerSample(event);
  if (screenEdgeResizeAnimationFrame) return;
  screenEdgeResizeAnimationFrame = requestAnimationFrame(() => {
    screenEdgeResizeAnimationFrame = 0;
    const sample = pendingScreenEdgeResizeSample;
    pendingScreenEdgeResizeSample = null;
    if (sample) applyScreenEdgeResizeMove(sample);
  });
}
function stopScreenEdgeResize(){
  if (!activeScreenEdgeResize) return;
  const orientation = activeScreenEdgeResize.orientation || currentEditorOrientation();
  activeScreenEdgeResize = null;
  pendingScreenEdgeResizeSample = null;
  if (screenEdgeResizeAnimationFrame) {
    cancelAnimationFrame(screenEdgeResizeAnimationFrame);
    screenEdgeResizeAnimationFrame = 0;
  }
  window.removeEventListener('pointermove', handleScreenEdgeResizeMove);
  window.removeEventListener('pointerup', stopScreenEdgeResize);
  window.removeEventListener('pointercancel', stopScreenEdgeResize);
  persistScreenSizeState(orientation);
  updateScaleHandle();
}
screenPreviewSurface?.addEventListener('pointerdown', event => {
  const edge = hitTestScreenResizeEdge(event);
  if (!edge) return;
  if (beginScreenEdgeResize(event, edge)) event.stopImmediatePropagation();
}, true);
screenPreviewSurface?.addEventListener('pointerdown', event => {
  longPressTriggered = false;
  clearPreviewLongPress();
  beginScreenDrag(event);
  longPressTimer = setTimeout(openScreenContextMenu, 380);
});
['pointerup','pointerleave','pointercancel'].forEach(eventName => {
  screenPreviewSurface?.addEventListener(eventName, () => {
    clearPreviewLongPress();
  });
});
screenPreviewSurface?.addEventListener('click', (event) => {
  if (longPressTriggered || shouldIgnorePreviewReleaseClick()) {
    event.preventDefault();
    event.stopPropagation();
    return;
  }
  event.preventDefault();
  event.stopPropagation();
  hideControlRemoveMenu();
  screenContextMenu?.classList.remove('open');
  selectScreenFrame();
  persistScreenSizeState();
  showToast('Screen selected — drag it to move, or use the resize handle');
});
screenPreviewSurface?.addEventListener('contextmenu', (event) => {
  event.preventDefault();
  markPreviewReleaseClickIgnored();
  screenContextMenu?.classList.add('open');
});
document.addEventListener('click', (event) => {
  if (!screenContextMenu || !screenPreviewShell) return;
  if (!screenContextMenu.contains(event.target) && !screenPreviewShell.contains(event.target)) {
    screenContextMenu.classList.remove('open');
  }
});
screenContextMenu?.addEventListener('click', (event) => {
  event.stopPropagation();
});
emulatorPreview?.addEventListener('click', event => {
  if (shouldIgnorePreviewReleaseClick()) {
    event.preventDefault();
    event.stopPropagation();
    return;
  }
  if (
    event.target.closest('[data-layout-control]') ||
    event.target.closest('#previewScaleHandle') ||
    event.target.closest('#addControlFab') ||
    event.target.closest('#screenContextMenu') ||
    event.target.closest('#controlRemoveMenu')
  ) {
    return;
  }
  clearPreviewSelection();
  clearScreenFrameSelection();
  screenContextMenu?.classList.remove('open');
  hideControlRemoveMenu();
  persistScreenSizeState();
});
controlRemoveMenu?.addEventListener('click', event => {
  event.preventDefault();
  event.stopPropagation();
});
removeSelectedControlBtn?.addEventListener('click', event => {
  event.preventDefault();
  event.stopPropagation();
  const controlId = controlRemoveMenu?.dataset.controlId || previewLayoutState.selectedId;
  if (controlId) removePreviewControl(controlId);
});
document.addEventListener('pointerdown', event => {
  if (!controlRemoveMenu?.classList.contains('open')) return;
  if (controlRemoveMenu.contains(event.target) || event.target.closest('[data-layout-control]')) return;
  hideControlRemoveMenu();
});
addControlFab?.addEventListener('click', event => {
  event.preventDefault();
  event.stopPropagation();
  openAddControlModal();
});
closeAddControlModalBtn?.addEventListener('click', closeAddControlModal);
addControlModal?.addEventListener('click', event => {
  if (event.target === addControlModal) closeAddControlModal();
});
addControlItems.forEach(item => {
  item.addEventListener('click', () => addPreviewControl(item.dataset.controlType));
});
resetPreviewLayoutBtn?.addEventListener('click', () => {
  resetPreviewLayout();
  closeAddControlModal();
  showToast('Preview button layout reset');
});
// Persisted editor-only grid; never affects gameplay rendering.
applyScreenEditorGrid();
