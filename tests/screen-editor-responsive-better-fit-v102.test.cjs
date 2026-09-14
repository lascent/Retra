const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const editor = fs.readFileSync('app/src/main/assets/retra/screen-editor.js', 'utf8');
const interactions = fs.readFileSync('app/src/main/assets/retra/screen-editor-interactions.js', 'utf8');
const screenLogic = `${editor}\n${interactions}`;
const html = fs.readFileSync('app/src/main/assets/retra/index.html', 'utf8');
const css = fs.readFileSync('app/src/main/assets/retra/style-editor-modern.css', 'utf8');
const nativeLayout = fs.readFileSync('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt', 'utf8');
const profiles = fs.readFileSync('app/src/main/assets/retra/layout-profiles.js', 'utf8');

test('Screen Editor exposes Best Fit and persists it as a real layout mode', () => {
  assert.match(html, /data-size-mode="betterfit"[^>]*>Best Fit</);
  assert.match(editor, /betterfit: 'Best Fit'/);
  assert.match(editor, /'fullscreen','centered','betterfit','best','stretch','custom'/);
  assert.match(profiles, /'fullscreen','centered','betterfit','best','stretch','custom'/);
});

test('Best Fit matches the supplied landscape reference proportions', () => {
  assert.match(editor, /mode === 'betterfit' \? 0\.68/);
  assert.match(editor, /mode === 'betterfit' \? 1\.00/);
  assert.match(nativeLayout, /"betterfit" -> 0\.68f/);
  assert.match(nativeLayout, /"betterfit" -> 1\.00f/);
});

test('game screen itself is directly draggable and remains inside the editor canvas', () => {
  assert.match(screenLogic, /function beginScreenDrag\(/);
  assert.match(screenLogic, /function applyScreenDragMove\(/);
  assert.match(screenLogic, /Math\.hypot\(dx, dy\) < 3/);
  assert.match(screenLogic, /screenPreviewSurface\?\.setPointerCapture/);
  assert.match(screenLogic, /drag\.startLeft \+ dx/);
  assert.match(screenLogic, /drag\.startTop \+ dy/);
  assert.match(screenLogic, /persistScreenSizeState\(drag\.orientation\)/);
});

test('screen drag, resize handle and edge resize are animation-frame coalesced', () => {
  assert.match(screenLogic, /screenDragAnimationFrame = requestAnimationFrame/);
  assert.match(screenLogic, /screenScaleAnimationFrame = requestAnimationFrame/);
  assert.match(screenLogic, /screenEdgeResizeAnimationFrame = requestAnimationFrame/);
  assert.match(screenLogic, /latestPointerSample\(event\)/);
  assert.match(screenLogic, /screenDragAnimationFrame = requestAnimationFrame/);
});

test('screen resize handle is adaptive, touch-safe and anchored from its opposite corner', () => {
  assert.match(editor, /adaptiveControlScaleHandlePlacement\([\s\S]*frameRect/);
  assert.match(editor, /dataset\.scaleDirectionX = String\(placement\.xDirection\)/);
  assert.match(screenLogic, /anchorX: xDirection > 0 \? startLeft : startLeft \+ frameRect\.width/);
  assert.match(screenLogic, /anchorY: yDirection > 0 \? startTop : startTop \+ frameRect\.height/);
  assert.match(css, /#screenSizePage \.preview-screen-surface\{[\s\S]*touch-action:none !important/);
  assert.match(css, /#screenSizePage \.preview-scale-handle\.screen-scale-mode\{[\s\S]*width:44px !important/);
});
