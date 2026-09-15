// Retra Screen Editor controller UI helpers.
// Loaded before screen-editor.js; functions resolve the editor's global lexical
// state when invoked after screen-editor.js has initialized its bindings.

const DEFAULT_CONTROLLER_SCALE_LANDSCAPE_PRIMARY = 1.05;
const landscapePrimaryBaseControls = new Set(['shoulderLeft', 'shoulderRight', 'dpad', 'ab']);

function defaultBaseControllerScale(controlId, orientation = currentEditorOrientation()){
  if (orientation === 'landscape' && landscapePrimaryBaseControls.has(controlId)) {
    return DEFAULT_CONTROLLER_SCALE_LANDSCAPE_PRIMARY;
  }
  return defaultControllerScale(orientation);
}

// A control type can exist only once in each orientation-specific layout.
// The built-in A/B cluster also owns the A and B gameplay actions, so the
// individual A/B add-items are hidden while that cluster is present.
function previewControlTypesConflict(firstType, secondType){
  if (!firstType || !secondType) return false;
  if (firstType === secondType) return true;
  return (
    (firstType === 'ab' && (secondType === 'buttonA' || secondType === 'buttonB')) ||
    (secondType === 'ab' && (firstType === 'buttonA' || firstType === 'buttonB'))
  );
}

function isPreviewControlTypePlaced(type, exceptControlId = null){
  return Object.entries(previewLayoutState.controls).some(([controlId, state]) => {
    if (!state || controlId === exceptControlId || state.hidden === true) return false;
    return previewControlTypesConflict(type, state.type || controlId);
  });
}

function normalizePreviewExtraControls(){
  if (!previewLayoutState || !previewLayoutState.controls) return false;
  const seenTypes = [];
  let changed = false;
  Object.entries(previewLayoutState.controls).forEach(([controlId, state]) => {
    if (!state || state.base || state.hidden) return;
    const type = state.type || controlId;
    const conflictsExisting = seenTypes.some(existingType => previewControlTypesConflict(type, existingType));
    if (conflictsExisting) {
      previewControlElements.get(controlId)?.remove();
      previewControlElements.delete(controlId);
      delete previewLayoutState.controls[controlId];
      changed = true;
      return;
    }
    seenTypes.push(type);
  });
  return changed;
}

function setAddControlItemAvailability(item, alreadyAdded){
  item.hidden = alreadyAdded;
  item.disabled = alreadyAdded;
  item.classList.toggle('is-already-added', alreadyAdded);
  item.setAttribute('aria-hidden', alreadyAdded ? 'true' : 'false');
  item.style.display = alreadyAdded ? 'none' : '';
  item.style.pointerEvents = alreadyAdded ? 'none' : '';
}

function refreshAddControlAvailability(){
  if (!addControlList) return;

  const removedDuplicates = normalizePreviewExtraControls();
  if (removedDuplicates) {
    persistPreviewLayoutState?.();
  }

  addControlItems.forEach(item => {
    const type = item.dataset.controlType;
    const alreadyAdded = Boolean(type && isPreviewControlTypePlaced(type));
    setAddControlItemAvailability(item, alreadyAdded);
  });

  let emptyState = addControlList.querySelector('.add-control-empty-state');
  const hasAvailableExtra = addControlItems.some(item => item.style.display !== 'none' && !item.hidden);
  const hasRestoreItems = Boolean(addControlList.querySelector('.restore-default-control-item'));

  if (!hasAvailableExtra && !hasRestoreItems) {
    if (!emptyState) {
      emptyState = document.createElement('div');
      emptyState.className = 'add-control-empty-state';
      emptyState.textContent = 'All available controls are already on this screen.';
      addControlList.appendChild(emptyState);
    }
  } else {
    emptyState?.remove();
  }
}

function removeConflictingExtraPreviewControls(type, exceptControlId = null){
  Object.entries(previewLayoutState.controls).forEach(([controlId, state]) => {
    if (!state || state.base || state.hidden || controlId === exceptControlId) return;
    if (!previewControlTypesConflict(type, state.type)) return;
    previewControlElements.get(controlId)?.remove();
    previewControlElements.delete(controlId);
    delete previewLayoutState.controls[controlId];
  });
}

function makeExtraControlMarkup(type){
  switch (type) {
    case 'buttonA':
      return '<span class="extra-control-face circle-text">A</span>';
    case 'buttonB':
      return '<span class="extra-control-face circle-text">B</span>';
    case 'comboAB':
      return '<span class="extra-control-face circle-text">AB</span>';
    case 'comboLR':
      return '<span class="extra-control-face circle-text">LR</span>';
    case 'comboLA':
      return '<span class="extra-control-face circle-text">LA</span>';
    case 'comboLB':
      return '<span class="extra-control-face circle-text">LB</span>';
    case 'comboRA':
      return '<span class="extra-control-face circle-text">RA</span>';
    case 'comboRB':
      return '<span class="extra-control-face circle-text">RB</span>';
    case 'turboAB':
      return '<span class="extra-control-face turbo-face"><span>B</span><span>A</span></span>';
    case 'quickLoad':
      return '<span class="extra-control-face square-icon myboy-menu-button utility-menu-button"><svg class="control-svg myboy-reference-icon quick-load-reference-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M7.35 16.35c.95-4.15 4.1-6.95 8.35-7.55"></path><path d="M12.85 6.15 17.85 7.95l-3 4.05"></path></svg></span>';
    case 'quickSave':
      return '<span class="extra-control-face square-icon myboy-menu-button utility-menu-button"><svg class="control-svg myboy-reference-icon quick-save-reference-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5.5 4.5h10.8l2.2 2.2v12.8h-13z"></path><path d="M8.2 4.5v5h7v-5"></path><path d="M8.4 13h7.2"></path><path d="M8.4 16h7.2"></path></svg></span>';
    case 'fastForward':
      return '<span class="extra-control-face square-icon myboy-menu-button utility-menu-button"><svg class="control-svg control-svg-fast myboy-reference-icon fast-forward-reference-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m5.5 6.5 5.5 5.5-5.5 5.5"></path><path d="m11 6.5 5.5 5.5-5.5 5.5"></path></svg></span>';
    case 'screenshot':
      return '<span class="extra-control-face square-icon"><svg class="control-svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 7.5 8.5 5h7L17 7.5h2A2 2 0 0 1 21 9.5v8A2 2 0 0 1 19 19.5H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z"></path><circle cx="12" cy="13" r="3.2"></circle></svg></span>';
    default:
      return `<span class="extra-control-face circle-text">${(previewControlCatalog[type]?.label || '?').slice(0, 2)}</span>`;
  }
}


function makeAddControlItemInnerMarkup(type){
  const label = previewControlCatalog[type]?.label || 'Control';
  switch (type) {
    case 'menu':
      return `<span class="add-control-icon square"><svg class="control-svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M5.75 7.5h12.5M5.75 12h12.5M5.75 16.5h12.5"></path></svg></span><span class="add-control-label">${label}</span>`;
    case 'shoulderLeft':
      return `<span class="add-control-icon circle">L</span><span class="add-control-label">${label}</span>`;
    case 'shoulderRight':
      return `<span class="add-control-icon circle">R</span><span class="add-control-label">${label}</span>`;
    case 'dpad':
      return `<span class="add-control-icon square"><svg class="control-svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M10 3h4v7h7v4h-7v7h-4v-7H3v-4h7z"></path></svg></span><span class="add-control-label">${label}</span>`;
    case 'startSelect':
      return `<span class="add-control-icon circle">SS</span><span class="add-control-label">${label}</span>`;
    case 'ab':
      return `<span class="add-control-icon duo"><span>B</span><span>A</span></span><span class="add-control-label">${label}</span>`;
    case 'buttonA':
      return '<span class="add-control-icon circle">A</span><span class="add-control-label">Button A</span>';
    case 'buttonB':
      return '<span class="add-control-icon circle">B</span><span class="add-control-label">Button B</span>';
    case 'comboAB':
      return '<span class="add-control-icon circle">AB</span><span class="add-control-label">A+B</span>';
    case 'comboLR':
      return '<span class="add-control-icon circle">LR</span><span class="add-control-label">TL+TR</span>';
    case 'turboAB':
      return '<span class="add-control-icon duo"><span>B</span><span>A</span></span><span class="add-control-label">A/B turbo</span>';
    case 'comboLA':
      return '<span class="add-control-icon circle">LA</span><span class="add-control-label">TL+A</span>';
    case 'comboLB':
      return '<span class="add-control-icon circle">LB</span><span class="add-control-label">TL+B</span>';
    case 'comboRA':
      return '<span class="add-control-icon circle">RA</span><span class="add-control-label">TR+A</span>';
    case 'comboRB':
      return '<span class="add-control-icon circle">RB</span><span class="add-control-label">TR+B</span>';
    case 'quickLoad':
      return '<span class="add-control-icon square"><svg class="control-svg myboy-reference-icon quick-load-reference-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M7.35 16.35c.95-4.15 4.1-6.95 8.35-7.55"></path><path d="M12.85 6.15 17.85 7.95l-3 4.05"></path></svg></span><span class="add-control-label">Quick load</span>';
    case 'quickSave':
      return '<span class="add-control-icon square"><svg class="control-svg myboy-reference-icon quick-save-reference-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5.5 4.5h10.8l2.2 2.2v12.8h-13z"></path><path d="M8.2 4.5v5h7v-5"></path><path d="M8.4 13h7.2"></path><path d="M8.4 16h7.2"></path></svg></span><span class="add-control-label">Quick save</span>';
    case 'fastForward':
      return '<span class="add-control-icon square"><svg class="control-svg control-svg-fast myboy-reference-icon fast-forward-reference-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m5.5 6.5 5.5 5.5-5.5 5.5"></path><path d="m11 6.5 5.5 5.5-5.5 5.5"></path></svg></span><span class="add-control-label">Fast forward</span>';
    case 'screenshot':
      return '<span class="add-control-icon square"><svg class="control-svg" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 7.5 8.5 5h7L17 7.5h2A2 2 0 0 1 21 9.5v8A2 2 0 0 1 19 19.5H5a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z"></path><circle cx="12" cy="13" r="3.2"></circle></svg></span><span class="add-control-label">Screenshot</span>';
    default:
      return `<span class="add-control-icon circle">${label.slice(0, 2).toUpperCase()}</span><span class="add-control-label">${label}</span>`;
  }
}

