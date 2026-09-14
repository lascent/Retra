const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const editor = fs.readFileSync('app/src/main/assets/retra/screen-editor.js', 'utf8');
const interactions = fs.readFileSync('app/src/main/assets/retra/screen-editor-interactions.js', 'utf8');
const css = fs.readFileSync('app/src/main/assets/retra/style-editor-modern.css', 'utf8');
const html = fs.readFileSync('app/src/main/assets/retra/index.html', 'utf8');

test('controller live drag updates only the active element instead of relaying out every controller', () => {
  const start = editor.indexOf('function applyPreviewDragMove(');
  const end = editor.indexOf('function beginPreviewControlDrag(', start);
  const body = editor.slice(start, end);
  assert.doesNotMatch(body, /applyPreviewLayoutControls\(\)/);
  assert.doesNotMatch(body, /getBoundingClientRect\(\)/);
  assert.doesNotMatch(body, /element\.style\.setProperty\('left'/);
  assert.match(body, /translate3d\(\$\{translateX\}px, \$\{translateY\}px, 0\) scale/);
  assert.match(body, /drag\.previewWidth/);
  assert.match(body, /drag\.scaledWidth/);
});

test('screen live drag avoids full responsive layout and handle geometry until release', () => {
  const start = interactions.indexOf('function applyScreenDragMove(');
  const end = interactions.indexOf('function handleScreenDragMove(', start);
  const body = interactions.slice(start, end);
  assert.doesNotMatch(body, /updateResponsiveScreenFrame\(\)/);
  assert.doesNotMatch(body, /updateScaleHandle\(\)/);
  assert.doesNotMatch(body, /getBoundingClientRect\(\)/);
  assert.doesNotMatch(body, /previewScreenFrame\.style\.left = `\$\{left\}px`/);
  assert.match(body, /previewScreenFrame\.style\.transform[\s\S]*translate3d/);
  assert.match(body, /enterCustomScreenGestureMode\(\)/);
});

test('live drag styling disables expensive motion effects and Best Fit is the visible label', () => {
  assert.match(css, /\.layout-control\.is-dragging[\s\S]*transition:none !important/);
  assert.match(css, /\.layout-control\.is-dragging[\s\S]*filter:none !important/);
  assert.match(css, /\.layout-control\.is-dragging[\s\S]*will-change:transform !important/);
  assert.match(html, /data-size-mode="betterfit"[^>]*>Best Fit</);
  assert.match(editor, /betterfit: 'Best Fit'/);
});
