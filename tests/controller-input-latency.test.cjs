const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const layout = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const gameplay = read('app/src/main/java/com/retra/emulator/GameplayController.kt');
const multiplayer = read('app/src/main/java/com/retra/emulator/MultiplayerController.kt');
const native = read('app/src/main/cpp/native-lib.cpp');
const xml = read('app/src/main/res/layout/activity_main.xml');

test('A/B and single-key controls assert on ACTION_DOWN with pointer ownership and thumb-drift retention', () => {
  assert.match(layout, /internal fun MainActivity\.bindKey\(view: View, key: Int\)/);
  assert.match(layout, /activePointerId = MotionEvent\.INVALID_POINTER_ID/);
  assert.match(layout, /MotionEvent\.ACTION_DOWN[\s\S]*applyPressedState\(v, true\)/);
  assert.match(layout, /MotionEvent\.ACTION_MOVE/);
  assert.match(layout, /event\.findPointerIndex\(activePointerId\)/);
  assert.match(layout, /ViewConfiguration\.get\(view\.context\)\.scaledTouchSlop/);
  assert.match(layout, /MotionEvent\.ACTION_POINTER_UP/);
  assert.match(layout, /MotionEvent\.ACTION_CANCEL/);
  assert.match(layout, /requestDisallowInterceptTouchEvent\(true\)/);
});

test('controller touch path suppresses duplicate Android-side key transitions', () => {
  assert.match(activity, /internal var activeGameplayKeyMask = 0/);
  assert.match(multiplayer, /if \(wasPressed == pressed\) return/);
  assert.match(multiplayer, /activeGameplayKeyMask = if \(pressed\)/);
  assert.match(gameplay, /val heldMask = activeGameplayKeyMask/);
  assert.match(gameplay, /if \(heldMask and \(1 shl key\) == 0\) continue/);
});

test('sub-frame native taps are latched so mGBA sees at least one emulated-frame press', () => {
  assert.match(native, /static std::atomic<uint32_t> keyPressLatch\{0\}/);
  assert.match(native, /pressLatch->fetch_or\(bit, std::memory_order_relaxed\)/);
  assert.match(native, /const uint32_t tappedKeys = keyPressLatch\.exchange\(0, std::memory_order_relaxed\)/);
  assert.match(native, /core->setKeys\(core, heldKeys \| tappedKeys\)/);
  assert.match(native, /player->keyPressLatch\.fetch_or\(risingEdges, std::memory_order_relaxed\)/);
  assert.match(native, /player->core->setKeys\(player->core, heldKeys \| tappedKeys\)/);
});

test('lifecycle cancellation clears pending tap latches and all pressed visuals', () => {
  assert.match(activity, /external fun clearKeyPressLatches\(\)/);
  assert.match(activity, /onPause\(\)[\s\S]*releaseAllKeys\(\)/);
  assert.match(gameplay, /clearKeyPressLatches\(\)/);
  assert.match(gameplay, /clearPressedState\(binding\.emulatorViewport\)/);
});

test('gameplay view hierarchy explicitly supports split multi-touch for D-pad plus A/B', () => {
  assert.match(xml, /@\+id\/emulatorViewport[\s\S]*?android:splitMotionEvents="true"/);
  assert.match(xml, /@\+id\/abContainer[\s\S]*?android:splitMotionEvents="true"/);
});
