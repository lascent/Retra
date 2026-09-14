/* Retra v1.0.0 Screen Editor interaction helpers.
   Loaded after screen-editor.js so the core layout module stays bounded. */

let pendingPreviewDragSample = null;
let pendingPreviewScaleSample = null;
let previewDragAnimationFrame = 0;
let previewScaleAnimationFrame = 0;

let activeScreenScale = null;
let activeScreenDrag = null;
let pendingScreenScaleSample = null;
let pendingScreenDragSample = null;
let screenScaleAnimationFrame = 0;
let screenDragAnimationFrame = 0;

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

function stopScreenScale(){
  if (!activeScreenScale) return;
  const editedOrientation = activeScreenScale.orientation || currentEditorOrientation();
  const pointerId = activeScreenScale.pointerId;
  activeScreenScale = null;
  pendingScreenScaleSample = null;
  if (screenScaleAnimationFrame) {
    cancelAnimationFrame(screenScaleAnimationFrame);
    screenScaleAnimationFrame = 0;
  }
  try {
    if (previewScaleHandle?.hasPointerCapture?.(pointerId)) {
      previewScaleHandle.releasePointerCapture(pointerId);
    }
  } catch (_) {}
  window.removeEventListener('pointermove', handleScreenScaleMove);
  window.removeEventListener('pointerup', stopScreenScale);
  window.removeEventListener('pointercancel', stopScreenScale);
  persistScreenSizeState(editedOrientation);
  updateScaleHandle();
}

function applyScreenScaleMove(sample){
  if (!activeScreenScale || !emulatorPreview) return;

  const previewRect = emulatorPreview.getBoundingClientRect();
  const dx = sample.clientX - activeScreenScale.startX;
  const dy = sample.clientY - activeScreenScale.startY;
  const xDirection = activeScreenScale.xDirection;
  const yDirection = activeScreenScale.yDirection;

  // Dragging away from whichever adaptive corner currently owns the handle
  // grows the screen. Dragging back toward the opposite anchored corner
  // shrinks it. This feels much more direct than scaling around the center.
  const xScale = 1 + (dx * xDirection) / Math.max(1, activeScreenScale.startWidth);
  const yScale = 1 + (dy * yDirection) / Math.max(1, activeScreenScale.startHeight);
  const requestedScale = (xScale + yScale) / 2;

  const minWidth = Math.max(96, previewRect.width * 0.20);
  const minHeight = Math.max(72, previewRect.height * 0.16);
  const minScale = Math.max(
    minWidth / activeScreenScale.startWidth,
    minHeight / activeScreenScale.startHeight
  );

  const maxWidth =
    xDirection > 0
      ? previewRect.width - activeScreenScale.startLeft
      : activeScreenScale.startLeft + activeScreenScale.startWidth;
  const maxHeight =
    yDirection > 0
      ? previewRect.height - activeScreenScale.startTop
      : activeScreenScale.startTop + activeScreenScale.startHeight;
  const maxScale = Math.max(
    minScale,
    Math.min(
      maxWidth / Math.max(1, activeScreenScale.startWidth),
      maxHeight / Math.max(1, activeScreenScale.startHeight)
    )
  );

  const scale = clampPreviewValue(requestedScale, minScale, maxScale);
  const width = activeScreenScale.startWidth * scale;
  const height = activeScreenScale.startHeight * scale;
  const left =
    xDirection > 0
      ? activeScreenScale.startLeft
      : activeScreenScale.anchorX - width;
  const top =
    yDirection > 0
      ? activeScreenScale.startTop
      : activeScreenScale.anchorY - height;

  enterCustomScreenGestureMode();
  setCurrentCustomFrame({
    left: clampPreviewValue(left, 0, Math.max(0, previewRect.width - width)) / previewRect.width,
    top: clampPreviewValue(top, 0, Math.max(0, previewRect.height - height)) / previewRect.height,
    width: width / previewRect.width,
    height: height / previewRect.height
  });

  // Only repaint the game frame during the gesture. Re-laying out every
  // controller on every pointer event causes WebView jank on lower-end phones.
  updateResponsiveScreenFrame();
  updateScaleHandle();
}

function handleScreenScaleMove(event){
  if (!activeScreenScale) return;
  pendingScreenScaleSample = latestPointerSample(event);
  if (screenScaleAnimationFrame) return;
  screenScaleAnimationFrame = requestAnimationFrame(() => {
    screenScaleAnimationFrame = 0;
    const sample = pendingScreenScaleSample;
    pendingScreenScaleSample = null;
    if (sample) applyScreenScaleMove(sample);
  });
}

function beginScreenScale(event){
  if (!emulatorPreview || !previewScreenFrame || !screenFrameSelected) return;
  event.preventDefault();
  event.stopPropagation();
  clearPreviewLongPress();
  hideControlRemoveMenu();

  const frameRect = previewScreenFrame.getBoundingClientRect();
  const previewRect = emulatorPreview.getBoundingClientRect();
  const startLeft = frameRect.left - previewRect.left;
  const startTop = frameRect.top - previewRect.top;
  const xDirection = Number(previewScaleHandle?.dataset.scaleDirectionX) || 1;
  const yDirection = Number(previewScaleHandle?.dataset.scaleDirectionY) || 1;

  activeScreenScale = {
    orientation: currentEditorOrientation(),
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    startLeft,
    startTop,
    startWidth: frameRect.width,
    startHeight: frameRect.height,
    xDirection,
    yDirection,
    anchorX: xDirection > 0 ? startLeft : startLeft + frameRect.width,
    anchorY: yDirection > 0 ? startTop : startTop + frameRect.height
  };

  try { previewScaleHandle?.setPointerCapture?.(event.pointerId); } catch (_) {}
  window.addEventListener('pointermove', handleScreenScaleMove);
  window.addEventListener('pointerup', stopScreenScale);
  window.addEventListener('pointercancel', stopScreenScale);
}

function stopScreenDrag(){
  if (!activeScreenDrag) return;
  const drag = activeScreenDrag;
  // Apply the freshest coalesced pointer sample before committing.
  if (pendingScreenDragSample) applyScreenDragMove(pendingScreenDragSample);

  if (drag.moved && previewScreenFrame) {
    const finalLeft = Number.isFinite(drag.lastLeft) ? drag.lastLeft : drag.startLeft;
    const finalTop = Number.isFinite(drag.lastTop) ? drag.lastTop : drag.startTop;
    setCurrentCustomFrame({
      left: finalLeft / Math.max(1, drag.previewWidth),
      top: finalTop / Math.max(1, drag.previewHeight),
      width: drag.width / Math.max(1, drag.previewWidth),
      height: drag.height / Math.max(1, drag.previewHeight)
    });
    previewScreenFrame.style.left = `${finalLeft}px`;
    previewScreenFrame.style.top = `${finalTop}px`;
    previewScreenFrame.style.right = 'auto';
    previewScreenFrame.style.bottom = 'auto';
    previewScreenFrame.style.transform = 'none';
  }

  activeScreenDrag = null;
  pendingScreenDragSample = null;
  if (screenDragAnimationFrame) {
    cancelAnimationFrame(screenDragAnimationFrame);
    screenDragAnimationFrame = 0;
  }
  previewScreenFrame?.classList.remove('is-dragging');
  emulatorPreview?.classList.remove('editor-drag-active');
  try {
    if (screenPreviewSurface?.hasPointerCapture?.(drag.pointerId)) {
      screenPreviewSurface.releasePointerCapture(drag.pointerId);
    }
  } catch (_) {}
  window.removeEventListener('pointermove', handleScreenDragMove);
  window.removeEventListener('pointerup', stopScreenDrag);
  window.removeEventListener('pointercancel', stopScreenDrag);

  if (drag.moved) {
    markPreviewReleaseClickIgnored(520);
    persistScreenSizeState(drag.orientation);
    updateScaleHandle();
  }
}

function applyScreenDragMove(sample){
  const drag = activeScreenDrag;
  if (!drag || !emulatorPreview || !previewScreenFrame) return;

  const dx = sample.clientX - drag.startX;
  const dy = sample.clientY - drag.startY;

  if (!drag.moved) {
    if (Math.hypot(dx, dy) < 3) return;
    drag.moved = true;
    clearPreviewLongPress();
    markPreviewReleaseClickIgnored(520);
    selectScreenFrame();
    // Enter custom mode once per gesture, not once per animation frame.
    enterCustomScreenGestureMode();

    // Freeze the current rendered geometry as the compositing baseline. This
    // removes any preset centering transform without changing what the user sees.
    previewScreenFrame.style.left = `${drag.startLeft}px`;
    previewScreenFrame.style.top = `${drag.startTop}px`;
    previewScreenFrame.style.right = 'auto';
    previewScreenFrame.style.bottom = 'auto';
    previewScreenFrame.style.transform = 'translate3d(0, 0, 0)';
    previewScreenFrame.classList.add('is-dragging');
    emulatorPreview.classList.add('editor-drag-active');
    previewScaleHandle?.classList.remove('visible');
    previewScaleValue?.classList.remove('visible');
  }

  const left = clampPreviewValue(
    drag.startLeft + dx,
    0,
    Math.max(0, drag.previewWidth - drag.width)
  );
  const top = clampPreviewValue(
    drag.startTop + dy,
    0,
    Math.max(0, drag.previewHeight - drag.height)
  );

  drag.lastLeft = left;
  drag.lastTop = top;

  // Compositor-only hot path. Keep layout geometry frozen and move the screen
  // with translate3d so it tracks the finger without left/top layout work.
  previewScreenFrame.style.transform =
    `translate3d(${left - drag.startLeft}px, ${top - drag.startTop}px, 0)`;
}

function handleScreenDragMove(event){
  if (!activeScreenDrag) return;
  pendingScreenDragSample = latestPointerSample(event);
  if (screenDragAnimationFrame) return;
  screenDragAnimationFrame = requestAnimationFrame(() => {
    screenDragAnimationFrame = 0;
    const sample = pendingScreenDragSample;
    pendingScreenDragSample = null;
    if (sample) applyScreenDragMove(sample);
  });
}

function beginScreenDrag(event){
  if (!emulatorPreview || !previewScreenFrame || event.button > 0) return;
  const previewRect = emulatorPreview.getBoundingClientRect();
  const frameRect = previewScreenFrame.getBoundingClientRect();

  const startLeft = frameRect.left - previewRect.left;
  const startTop = frameRect.top - previewRect.top;
  activeScreenDrag = {
    orientation: currentEditorOrientation(),
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    startLeft,
    startTop,
    lastLeft: startLeft,
    lastTop: startTop,
    width: frameRect.width,
    height: frameRect.height,
    previewWidth: previewRect.width,
    previewHeight: previewRect.height,
    moved: false
  };

  try { screenPreviewSurface?.setPointerCapture?.(event.pointerId); } catch (_) {}
  window.addEventListener('pointermove', handleScreenDragMove);
  window.addEventListener('pointerup', stopScreenDrag);
  window.addEventListener('pointercancel', stopScreenDrag);
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
