const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
const layout = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');

test('D-pad and every gameplay controller share one vibration and sound profile', () => {
  assert.match(touch, /performUnifiedControllerHaptic\(view: View, force: Boolean = false\)/);
  assert.match(touch, /VibrationEffect\.createOneShot\(16L, amplitude\)/);
  assert.match(touch, /hasAmplitudeControl\(\)\) 150/);
  assert.match(touch, /playSoundEffect\(SoundEffectConstants\.CLICK, 0\.060f\)/);
  assert.match(touch, /if \(nextPressed\) performControllerHaptic\(v\)/);
  assert.match(touch, /performDpadPressFeedback\(view\)/);
  assert.match(touch, /if \(nextMask != 0\) performControllerHaptic\(binding\.dpadContainer, directional = true\)/);
  assert.match(layout, /if \(nextPressed\) performControllerHaptic\(v\)/);
  assert.match(layout, /performControllerHaptic\(binding\.menuButton\)/);
  assert.match(layout, /performControllerHaptic\(binding\.quickSaveButton\)/);
  assert.match(layout, /performControllerHaptic\(binding\.quickLoadButton\)/);
  assert.doesNotMatch(touch, /0\.055f|0\.075f/);
});
