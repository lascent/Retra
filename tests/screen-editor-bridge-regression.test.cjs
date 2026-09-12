const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const shell = fs.readFileSync('app/src/main/assets/retra/app-shell.js', 'utf8');
const editor = fs.readFileSync('app/src/main/assets/retra/screen-editor.js', 'utf8');

test('default controller layout bridge calls AndroidBridge instead of recursively calling itself', () => {
  assert.match(shell, /else if \(typeof window\.AndroidBridge\.setControllerLayout === 'function'\) \{\s*window\.AndroidBridge\.setControllerLayout\(json\);/);
  assert.doesNotMatch(shell, /else if \(typeof window\.AndroidBridge\.setControllerLayout === 'function'\) \{\s*nativeSetControllerLayout\(json\);/);
});

test('controller rendering does not persist layout on every pointer-move repaint', () => {
  const applyStart = editor.indexOf('function applyPreviewLayoutControls()');
  const dragStart = editor.indexOf('let activePreviewDrag', applyStart);
  const body = editor.slice(applyStart, dragStart);
  assert.doesNotMatch(body, /selectPreviewControl\(previewLayoutState\.selectedId\)/);
  assert.match(body, /updateScaleHandle\(\)/);
});

test('long press is not cancelled merely because pointer leaves visual control bounds', () => {
  const attachStart = editor.indexOf('function attachPreviewControlEvents');
  const registerStart = editor.indexOf('function registerPreviewControlElement', attachStart);
  const body = editor.slice(attachStart, registerStart);
  assert.match(body, /\['pointerup', 'pointercancel'\]/);
  assert.doesNotMatch(body, /\['pointerup', 'pointercancel', 'pointerleave'\]/);
});
