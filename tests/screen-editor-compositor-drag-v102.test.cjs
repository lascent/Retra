const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const editor = fs.readFileSync('app/src/main/assets/retra/screen-editor.js', 'utf8');
const interactions = fs.readFileSync('app/src/main/assets/retra/screen-editor-interactions.js', 'utf8');
const css = fs.readFileSync('app/src/main/assets/retra/style-editor-modern.css', 'utf8');

test('controller drag uses translate3d during the live gesture and commits normalized position only on release', () => {
  const moveStart = editor.indexOf('function applyPreviewDragMove(');
  const moveEnd = editor.indexOf('function beginPreviewControlDrag(', moveStart);
  const moveBody = editor.slice(moveStart, moveEnd);
  const stopStart = editor.indexOf('function stopPreviewDrag(');
  const stopEnd = editor.indexOf('function handlePreviewDragMove(', stopStart);
  const stopBody = editor.slice(stopStart, stopEnd);

  assert.match(moveBody, /translate3d\(\$\{translateX\}px, \$\{translateY\}px, 0\) scale\(\$\{drag\.scale\}\)/);
  assert.doesNotMatch(moveBody, /state\.x\s*=/);
  assert.doesNotMatch(moveBody, /state\.y\s*=/);
  assert.doesNotMatch(moveBody, /setProperty\('left'/);
  assert.doesNotMatch(moveBody, /setProperty\('top'/);
  assert.match(stopBody, /state\.x = finalLeft/);
  assert.match(stopBody, /state\.y = finalTop/);
  assert.match(stopBody, /setProperty\('transform', `scale\(\$\{drag\.scale\}\)`/);
});

test('game screen drag also stays compositor-only until pointer release', () => {
  const moveStart = interactions.indexOf('function applyScreenDragMove(');
  const moveEnd = interactions.indexOf('function handleScreenDragMove(', moveStart);
  const moveBody = interactions.slice(moveStart, moveEnd);
  const stopStart = interactions.indexOf('function stopScreenDrag(');
  const stopEnd = interactions.indexOf('function applyScreenDragMove(', stopStart);
  const stopBody = interactions.slice(stopStart, stopEnd);

  assert.match(moveBody, /previewScreenFrame\.style\.transform[\s\S]*translate3d/);
  assert.doesNotMatch(moveBody, /setCurrentCustomFrame\(/);
  assert.doesNotMatch(moveBody, /previewScreenFrame\.style\.left = `\$\{left\}px`/);
  assert.doesNotMatch(moveBody, /previewScreenFrame\.style\.top = `\$\{top\}px`/);
  assert.match(stopBody, /setCurrentCustomFrame\(/);
  assert.match(stopBody, /persistScreenSizeState\(drag\.orientation\)/);
});

test('dragging layers advertise transform compositing rather than left-top layout animation', () => {
  assert.match(css, /\.layout-control\.is-dragging[\s\S]*will-change:transform !important/);
  assert.match(css, /\.preview-screen-frame\.is-dragging[\s\S]*backface-visibility:hidden/);
  assert.doesNotMatch(css, /\.layout-control\.is-dragging[\s\S]*will-change:left, top !important/);
});
