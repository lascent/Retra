/* Retra v1.0.0 Screen Editor interaction helpers.
   Loaded after screen-editor.js so the core layout module stays bounded. */

let pendingPreviewDragSample = null;
let pendingPreviewScaleSample = null;
let previewDragAnimationFrame = 0;
let previewScaleAnimationFrame = 0;

function latestPointerSample(event){
  const coalesced = typeof event.getCoalescedEvents === 'function' ? event.getCoalescedEvents() : null;
  const sample = coalesced?.length ? coalesced[coalesced.length - 1] : event;
  return {
    pointerId: sample.pointerId,
    clientX: sample.clientX,
    clientY: sample.clientY
  };
}

function flushPendingPreviewDrag(){
  if (previewDragAnimationFrame) {
    cancelAnimationFrame(previewDragAnimationFrame);
    previewDragAnimationFrame = 0;
  }
  const sample = pendingPreviewDragSample;
  pendingPreviewDragSample = null;
  if (sample) applyPreviewDragMove(sample);
}

function flushPendingPreviewScale(){
  if (previewScaleAnimationFrame) {
    cancelAnimationFrame(previewScaleAnimationFrame);
    previewScaleAnimationFrame = 0;
  }
  const sample = pendingPreviewScaleSample;
  pendingPreviewScaleSample = null;
  if (sample) applyPreviewScaleMove(sample);
}

function nudgePreviewControl(controlId, deltaX, deltaY){
  if (!ensurePreviewLayoutInitialized() || !emulatorPreview) return;
  const state = previewLayoutState.controls[controlId];
  const element = previewControlElements.get(controlId);
  if (!state || !element || state.hidden) return;

  const previewRect = emulatorPreview.getBoundingClientRect();
  const rect = element.getBoundingClientRect();
  const nextLeft = clampPreviewValue(
    rect.left - previewRect.left + deltaX,
    0,
    Math.max(0, previewRect.width - rect.width)
  );
  const nextTop = clampPreviewValue(
    rect.top - previewRect.top + deltaY,
    0,
    Math.max(0, previewRect.height - rect.height)
  );

  state.x = nextLeft / Math.max(1, previewRect.width);
  state.y = nextTop / Math.max(1, previewRect.height);
  selectPreviewControl(controlId);
  applyPreviewLayoutControls();
  persistPreviewLayoutState();
}

function resizePreviewControlFromKeyboard(controlId, deltaScale){
  if (!ensurePreviewLayoutInitialized() || !emulatorPreview) return;
  const state = previewLayoutState.controls[controlId];
  const element = previewControlElements.get(controlId);
  if (!state || !element || state.hidden) return;

  const previewRect = emulatorPreview.getBoundingClientRect();
  const rect = element.getBoundingClientRect();
  const startScale = clampPreviewValue(Number(state.scale) || 1, 0.65, 1.7);
  const nextScale = clampPreviewValue(startScale + deltaScale, 0.65, 1.7);
  const baseWidth = Number(element.dataset.baseWidth) || element.offsetWidth || 44;
  const baseHeight = Number(element.dataset.baseHeight) || element.offsetHeight || 44;
  const nextWidth = baseWidth * nextScale;
  const nextHeight = baseHeight * nextScale;

  // Keep the visual center stable while keyboard-resizing, then clamp to the editor.
  const centerX = rect.left - previewRect.left + rect.width / 2;
  const centerY = rect.top - previewRect.top + rect.height / 2;
  const nextLeft = clampPreviewValue(centerX - nextWidth / 2, 0, Math.max(0, previewRect.width - nextWidth));
  const nextTop = clampPreviewValue(centerY - nextHeight / 2, 0, Math.max(0, previewRect.height - nextHeight));

  state.scale = nextScale;
  state.x = nextLeft / Math.max(1, previewRect.width);
  state.y = nextTop / Math.max(1, previewRect.height);
  selectPreviewControl(controlId);
  applyPreviewLayoutControls();
  persistPreviewLayoutState();
}

let longPressTimer = null;
let longPressTriggered = false;
let ignorePreviewReleaseClickUntil = 0;

function shouldIgnorePreviewReleaseClick(){
  return Date.now() < ignorePreviewReleaseClickUntil;
}

function markPreviewReleaseClickIgnored(duration = 420){
  ignorePreviewReleaseClickUntil = Date.now() + duration;
}

function clearPreviewLongPress(){
  if (longPressTimer) {
    clearTimeout(longPressTimer);
    longPressTimer = null;
  }
}

function openScreenContextMenu(){
  longPressTriggered = true;
  markPreviewReleaseClickIgnored();
  screenContextMenu?.classList.add('open');
}

// Track the actual editor canvas, not just window resize events. This keeps
// the selection/resize affordances attached to their controls when Android
// changes safe-area/inset geometry or a foldable/windowed layout is resized.
if (typeof ResizeObserver !== 'undefined' && emulatorPreview) {
  const editorCanvasObserver = new ResizeObserver(() => {
    if (currentOrientationPage !== 'screenSizePage') return;
    requestAnimationFrame(() => {
      if (!previewLayoutInitialized || screenEditorOrientationSwitchInProgress) return;
      applyScreenSizePreview();
      applyPreviewLayoutControls();
      updateScaleHandle();
    });
  });
  editorCanvasObserver.observe(emulatorPreview);
}
