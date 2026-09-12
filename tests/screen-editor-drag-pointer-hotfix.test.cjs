const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const js = fs.readFileSync('app/src/main/assets/retra/screen-editor.js', 'utf8');
const css = fs.readFileSync('app/src/main/assets/retra/style-editor-modern.css', 'utf8');

test('controller moving and adaptive resizing keep independent pointer capture', () => {
  assert.match(js, /element\.setPointerCapture\?\.\(event\.pointerId\)/);
  assert.match(js, /previewScaleHandle\?\.setPointerCapture\?\.\(event\.pointerId\)/);
  assert.match(js, /pointerId:\s*event\.pointerId/);
  assert.match(js, /releasePointerCapture/);
});

test('adaptive resize handle stays touch-safe and mostly outside controller drag area', () => {
  assert.match(js, /const overlap = 0\.18/);
  assert.match(css, /\.preview-scale-handle:not\(\.screen-scale-mode\)[\s\S]*touch-action:none !important/);
  assert.match(css, /\.preview-scale-handle:not\(\.screen-scale-mode\) svg[\s\S]*pointer-events:none !important/);
});
