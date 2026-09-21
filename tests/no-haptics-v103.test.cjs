const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');

test('v1.0.3 controller haptics use system feedback without raw vibration permissions', () => {
  const manifest = read('app/src/main/AndroidManifest.xml');
  const feedback = read('app/src/main/java/com/retra/emulator/ControllerFeedbackManager.kt');
  assert.match(feedback, /HapticFeedbackConstants\.(?:CLOCK_TICK|SEGMENT_FREQUENT_TICK)/);
  assert.match(feedback, /performHapticFeedback/);
  assert.doesNotMatch(feedback, /VibrationEffect|VibratorManager|\bVibrator\b/);
  assert.doesNotMatch(manifest, /android\.permission\.VIBRATE/);
});

test('controller sound and haptics remain independently configurable', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
  assert.match(html, /id="controllerSoundToggle"/);
  assert.match(html, /id="controllerHapticsToggle"/);
  assert.match(settings, /controllerSound/);
  assert.match(settings, /controllerHaptics/);
  assert.match(html, /<h2>Sound<\/h2>/);
});
