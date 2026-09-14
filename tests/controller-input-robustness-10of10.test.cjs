const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
const layout = touch + '\n' + read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const gameplay = read('app/src/main/java/com/retra/emulator/GameplayController.kt');
const multiplayer = read('app/src/main/java/com/retra/emulator/MultiplayerController.kt');

test('independent controller sources cannot release a key still held by another source', () => {
  assert.match(activity, /gameplayKeyHoldCounts = IntArray\(10\)/);
  assert.match(activity, /controllerInputGeneration = 0L/);
  assert.match(multiplayer, /internal fun MainActivity\.setGameplayKeyHeld\(key: Int, pressed: Boolean\)/);
  assert.match(multiplayer, /val previousCount = gameplayKeyHoldCounts\[key\]/);
  assert.match(multiplayer, /if \(previousCount == 0 \|\| nextCount == 0\)[\s\S]*setGameplayKey\(key, nextCount > 0\)/);
  assert.match(gameplay, /controllerInputGeneration\+\+/);
  assert.match(gameplay, /gameplayKeyHoldCounts\.fill\(0\)/);
});

test('D-pad has stale-stream recovery and releases on CANCEL/OUTSIDE', () => {
  assert.match(layout, /activeDpadPointerId != MotionEvent\.INVALID_POINTER_ID \|\| activeDpadMask != 0/);
  assert.match(layout, /MotionEvent\.ACTION_CANCEL,[\s\S]*MotionEvent\.ACTION_OUTSIDE[\s\S]*finishDpadGesture\(view\)/);
  assert.match(layout, /event\.findPointerIndex\(activeDpadPointerId\)/);
});

test('single keys use source-safe hold transitions and recover stale ownership', () => {
  assert.match(layout, /setGameplayKeyHeld\(key, nextPressed\)/);
  assert.match(layout, /gestureGeneration = controllerInputGeneration/);
  assert.match(layout, /gestureGeneration != controllerInputGeneration/);
  assert.match(layout, /MotionEvent\.ACTION_UP, MotionEvent\.ACTION_CANCEL, MotionEvent\.ACTION_OUTSIDE/);
});

test('combo controls own one pointer, retain thumb drift, and never steal another pointer', () => {
  const multi = layout.slice(layout.indexOf('internal fun MainActivity.bindMultiKeyControl'), layout.indexOf('internal fun MainActivity.bindTurboAbControl'));
  assert.match(multi, /activePointerId = MotionEvent\.INVALID_POINTER_ID/);
  assert.match(multi, /gestureGeneration = controllerInputGeneration/);
  assert.match(multi, /gestureGeneration != controllerInputGeneration/);
  assert.match(multi, /scaledTouchSlop/);
  assert.match(multi, /event\.findPointerIndex\(activePointerId\)/);
  assert.match(multi, /ACTION_POINTER_UP/);
  assert.match(multi, /ACTION_POINTER_DOWN -> true/);
  assert.match(multi, /ACTION_CANCEL, MotionEvent\.ACTION_OUTSIDE/);
  assert.match(multi, /setGameplayKeyHeld\(it, nextPressed\)/);
});

test('Turbo AB is pointer-owned and balances every generated A/B hold', () => {
  const turbo = layout.slice(layout.indexOf('internal fun MainActivity.bindTurboAbControl'), layout.indexOf('internal fun MainActivity.makeTurboAbControl'));
  assert.match(turbo, /activePointerId = MotionEvent\.INVALID_POINTER_ID/);
  assert.match(turbo, /gestureGeneration = controllerInputGeneration/);
  assert.match(turbo, /gestureGeneration != controllerInputGeneration/);
  assert.match(turbo, /setPulsePressed\(!pulsePressed\)/);
  assert.match(turbo, /setGameplayKeyHeld\(KEY_A, nextPressed\)/);
  assert.match(turbo, /setGameplayKeyHeld\(KEY_B, nextPressed\)/);
  assert.match(turbo, /ACTION_POINTER_UP/);
  assert.match(turbo, /ACTION_CANCEL, MotionEvent\.ACTION_OUTSIDE/);
  assert.match(turbo, /handler\.removeCallbacks\(pulse\)[\s\S]*setPulsePressed\(false\)/);
});
