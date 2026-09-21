const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const manager = read('app/src/main/java/com/retra/emulator/ControllerFeedbackManager.kt');
const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
const layout = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
const html = read('app/src/main/assets/retra/index.html');
const js = read('app/src/main/assets/retra/settings-ui.js');
const models = read('app/src/main/java/com/retra/emulator/RetraModels.kt');

 test('controller feedback is isolated from pointer ownership and uses system-tuned light haptics', () => {
  assert.match(manager, /class ControllerFeedbackManager/);
  assert.match(manager, /HapticFeedbackConstants\.CLOCK_TICK/);
  assert.match(manager, /HapticFeedbackConstants\.SEGMENT_FREQUENT_TICK/);
  assert.match(manager, /Build\.VERSION\.SDK_INT >= 34/);
  assert.match(manager, /FX_KEY_CLICK/);
  assert.match(manager, /HAPTIC_GAP_MS = 26L/);
  assert.match(manager, /SOUND_GAP_MS = 28L/);
  assert.doesNotMatch(manager, /Vibrator|VibrationEffect/);
  assert.doesNotMatch(touch, /AudioManager|performHapticFeedback|SystemClock/);
  assert.match(touch, /controllerFeedback\.perform/);
  assert.match(layout, /controllerFeedback\.perform/);
});

test('controller haptics are independently configurable and portable', () => {
  assert.match(settings, /put\("controllerHaptics", prefs\.getBoolean\(CONTROLLER_HAPTICS_PREF, true\)\)/);
  assert.match(settings, /"controllerHaptics" -> editor\.putBoolean\(CONTROLLER_HAPTICS_PREF/);
  assert.match(prefs, /"controller_haptics_v1"/);
  assert.match(html, /id="controllerHapticsToggle"/);
  assert.match(html, /Light smooth vibration feedback for every on-screen control/);
  assert.match(js, /controllerHapticsToggle/);
  assert.match(js, /'controllerHaptics'/);
});

test('shared ROM/import models no longer depend on nested MainActivity types', () => {
  for (const name of ['NativeLibraryItem', 'PendingPatchLaunch', 'PendingLocate']) {
    assert.match(models, new RegExp(`data class ${name}`));
  }
  const romUi = read('app/src/main/java/com/retra/emulator/RomUiController.kt');
  assert.doesNotMatch(romUi, /MainActivity\.(NativeLibraryItem|PendingPatchLaunch|PendingLocate)/);
});
