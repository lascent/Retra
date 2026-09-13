const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const layout = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/GameplayLayoutController.kt'), 'utf8');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');

function bodyBetween(start, end) {
  const a = layout.indexOf(start);
  assert.notEqual(a, -1, `missing ${start}`);
  const b = layout.indexOf(end, a + start.length);
  assert.notEqual(b, -1, `missing ${end}`);
  return layout.slice(a, b);
}

test('D-pad caches geometry instead of querying screen position on every MOVE', () => {
  const update = bodyBetween('internal fun MainActivity.updateDpadFromRawPoint', 'internal fun MainActivity.setActiveDpadMask');
  assert.doesNotMatch(update, /getLocationOnScreen/);
  assert.match(layout, /internal fun MainActivity.refreshDpadTouchGeometry/);
  assert.match(layout, /pad\.getLocationOnScreen\(dpadScreenLocation\)/);
  assert.match(layout, /scaledWidth = pad\.width \* kotlin\.math\.abs\(pad\.scaleX\)/);
  assert.match(layout, /scaledHeight = pad\.height \* kotlin\.math\.abs\(pad\.scaleY\)/);
});

test('D-pad uses pointer ownership and releases safely on cancellation', () => {
  assert.match(activity, /activeDpadPointerId = MotionEvent\.INVALID_POINTER_ID/);
  assert.match(layout, /event\.findPointerIndex\(activeDpadPointerId\)/);
  assert.match(layout, /MotionEvent\.ACTION_CANCEL/);
  assert.match(layout, /MotionEvent\.ACTION_POINTER_UP/);
  assert.match(layout, /finishDpadGesture/);
});

test('D-pad uses centre and diagonal hysteresis', () => {
  assert.match(layout, /if \(activeDpadMask == 0\) 0\.15f else 0\.10f/);
  assert.match(layout, /if \(wasDiagonal\) 0\.35f else 0\.44f/);
});

test('D-pad high-rate movement path is allocation-free and delta-only', () => {
  assert.doesNotMatch(layout, /mutableSetOf<Int>\(\)/);
  assert.doesNotMatch(layout, /setActiveDpadKeys/);
  assert.match(activity, /internal var activeDpadMask = 0/);
  assert.match(layout, /if \(activeDpadMask == nextMask\) return/);
  assert.match(layout, /if \(wasPressed == isPressed\) return/);
});
