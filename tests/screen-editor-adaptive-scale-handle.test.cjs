const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/screen-editor.js'), 'utf8');
const interactions = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/screen-editor-interactions.js'), 'utf8');

test('controller resize handle chooses the sides with the most free editor space', () => {
  assert.match(source, /function adaptiveControlScaleHandlePlacement\(/);
  assert.match(source, /freeRight >= freeLeft \? 1 : -1/);
  assert.match(source, /freeBottom >= freeTop \? 1 : -1/);
  assert.match(source, /dataset\.scaleCorner = placement\.corner/);
});

test('adaptive resize direction stays natural after the handle flips corners', () => {
  assert.match(source, /deltaX \* activePreviewScale\.xDirection/);
  assert.match(source, /deltaY \* activePreviewScale\.yDirection/);
  assert.match(source, /dataset\.scaleDirectionX/);
  assert.match(source, /dataset\.scaleDirectionY/);
});

test('left or top resize handles keep the opposite controller edge anchored', () => {
  assert.match(source, /if \(activePreviewScale\.xDirection < 0\)/);
  assert.match(source, /startWidth - scaledWidth/);
  assert.match(source, /if \(activePreviewScale\.yDirection < 0\)/);
  assert.match(source, /startHeight - scaledHeight/);
});


test('high-frequency editor gestures are coalesced to one animation frame', () => {
  assert.match(interactions, /getCoalescedEvents/);
  assert.match(source, /previewDragAnimationFrame = requestAnimationFrame/);
  assert.match(source, /previewScaleAnimationFrame = requestAnimationFrame/);
  assert.match(source, /flushPendingPreviewDrag\(\)/);
  assert.match(source, /flushPendingPreviewScale\(\)/);
});

test('Screen Editor supports keyboard precision and observes canvas resizing', () => {
  assert.match(source, /ArrowLeft/);
  assert.match(source, /ArrowRight/);
  assert.match(source, /event\.key === 'Delete'/);
  assert.match(source, /resizePreviewControlFromKeyboard/);
  assert.match(interactions, /function nudgePreviewControl/);
  assert.match(interactions, /function resizePreviewControlFromKeyboard/);
  assert.match(interactions, /new ResizeObserver/);
});
