const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
const manifest = read('app/src/main/AndroidManifest.xml');

test('every D-pad press and rolling direction change gets reliable consistent medium-strength feedback', () => {
  assert.match(manifest, /android\.permission\.VIBRATE/);
  assert.match(touch, /internal fun MainActivity\.performUnifiedControllerHaptic\(view: View, force: Boolean = false\)/);
  assert.match(touch, /VibratorManager::class\.java/);
  assert.match(touch, /VibrationEffect\.createOneShot\(16L, amplitude\)/);
  assert.match(touch, /if \(!force && now - lastControllerHapticAtMs < 20L\) return/);
  assert.match(touch, /performDpadPressFeedback\(view\)/);
  assert.match(touch, /performUnifiedControllerHaptic\(view, force = true\)/);
  assert.match(touch, /if \(activeDpadMask == nextMask\) return/);
  assert.match(touch, /if \(nextMask != 0\) performControllerHaptic\(binding\.dpadContainer, directional = true\)/);
});
