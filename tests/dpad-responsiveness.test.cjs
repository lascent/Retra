const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const layout = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/GameplayTouchController.kt'), 'utf8');
const activity = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const inputState = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/GameplayInputState.kt'), 'utf8');

function bodyBetween(start, end) {
  const a = layout.indexOf(start);
  assert.notEqual(a, -1, `missing ${start}`);
  const b = layout.indexOf(end, a + start.length);
  assert.notEqual(b, -1, `missing ${end}`);
  return layout.slice(a, b);
}

test('D-pad has one touch owner so A/B pointers cannot corrupt direction', () => {
  const bind = bodyBetween('internal fun MainActivity.bindDpad()', 'internal fun MainActivity.finishDpadGesture');
  assert.match(bind, /binding\.dpadContainer\.setOnTouchListener/);
  assert.match(bind, /direction\.setOnTouchListener\(null\)/);
  assert.match(bind, /direction\.isClickable = false/);
  assert.doesNotMatch(bind, /binding\.button(?:Up|Down|Left|Right)\.setOnTouchListener\(handler\)/);
});

test('D-pad derives direction only from the owned pointer local coordinates', () => {
  const update = bodyBetween('internal fun MainActivity.updateDpadFromMotionEvent', 'internal fun MainActivity.updateDpadFromLocalPoint');
  assert.match(update, /event\.findPointerIndex\(gameplayInputState\.activeDpadPointerId\)/g);
  assert.match(update, /event\.getX\(pointerIndex\)/);
  assert.match(update, /event\.getY\(pointerIndex\)/);
  assert.doesNotMatch(update, /rawX|getRawX|getLocationOnScreen/);

  const local = bodyBetween('internal fun MainActivity.updateDpadFromLocalPoint', 'internal fun MainActivity.setActiveDpadMask');
  assert.match(local, /val centerX = pad\.width \/ 2f/);
  assert.match(local, /val centerY = pad\.height \/ 2f/);
  assert.match(local, /minOf\(pad\.width, pad\.height\) \/ 2f/);
});

test('D-pad uses pointer ownership and releases safely on cancellation', () => {
  assert.match(inputState, /activeDpadPointerId: Int = MotionEvent\.INVALID_POINTER_ID/);
  assert.match(layout, /event\.findPointerIndex\(gameplayInputState\.activeDpadPointerId\)/g);
  assert.match(layout, /MotionEvent\.ACTION_CANCEL/);
  assert.match(layout, /MotionEvent\.ACTION_POINTER_UP/);
  assert.match(layout, /finishDpadGesture/);
});

test('D-pad uses centre and diagonal hysteresis', () => {
  assert.match(layout, /if \(gameplayInputState\.activeDpadMask == 0\) 0\.15f else 0\.10f/);
  assert.match(layout, /if \(wasDiagonal\) 0\.35f else 0\.44f/);
});

test('D-pad high-rate movement path is allocation-free and delta-only', () => {
  assert.doesNotMatch(layout, /mutableSetOf<Int>\(\)/);
  assert.doesNotMatch(layout, /setActiveDpadKeys/);
  assert.match(inputState, /var activeDpadMask: Int = 0/);
  assert.match(layout, /if \(gameplayInputState\.activeDpadMask == nextMask\) return/);
  assert.match(layout, /if \(wasPressed == isPressed\) return/);
});
