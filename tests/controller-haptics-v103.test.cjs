const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
const layout = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
const html = read('app/src/main/assets/retra/index.html');
const js = read('app/src/main/assets/retra/settings-ui.js');

test('controller haptics are enabled by default and user-configurable', () => {
  assert.match(activity, /CONTROLLER_HAPTICS_PREF = "controller_haptics_v1"/);
  assert.match(settings, /put\("controllerHaptics", prefs\.getBoolean\(CONTROLLER_HAPTICS_PREF, true\)\)/);
  assert.match(settings, /"controllerHaptics" -> editor\.putBoolean\(CONTROLLER_HAPTICS_PREF, value\.toBoolean\(\)\)/);
  assert.match(prefs, /"controller_haptics_v1"/);
  assert.match(html, /id="controllerHapticsToggle"/);
  assert.match(html, /Haptic feedback/);
  assert.match(js, /controllerHapticsToggle/);
  assert.match(js, /'controllerHaptics'/);
});

test('gameplay haptics use the native low-overhead feedback path', () => {
  assert.match(touch, /performControllerHaptic\(view: View, directional: Boolean = false\)/);
  assert.match(touch, /performUnifiedControllerHaptic\(view/);
  assert.match(touch, /VibrationEffect\.createOneShot\(16L, amplitude\)/);
  assert.match(touch, /HapticFeedbackConstants\.KEYBOARD_TAP/);
  assert.match(touch, /prefs\.getBoolean\(CONTROLLER_HAPTICS_PREF, true\)/);
  assert.match(touch, /performDpadPressFeedback\(view\)/);
  assert.match(touch, /if \(nextMask != 0\) performControllerHaptic\(binding\.dpadContainer, directional = true\)/);
  assert.match(touch, /if \(nextPressed\) performControllerHaptic\(v\)/);
  assert.match(layout, /keys\.forEach \{ setGameplayKeyHeld\(it, nextPressed\) \}[\s\S]*if \(nextPressed\) performControllerHaptic\(v\)/);
  assert.match(layout, /active = true\n\s*performControllerHaptic\(v\)/);
});

test('turbo vibration happens only on the initial press, not every turbo pulse', () => {
  const turbo = layout.slice(layout.indexOf('internal fun MainActivity.bindTurboAbControl'), layout.indexOf('internal fun MainActivity.makeTurboAbControl'));
  const pulse = turbo.slice(turbo.indexOf('val pulse = object'), turbo.indexOf('fun finishGesture'));
  assert.doesNotMatch(pulse, /performControllerHaptic/);
  assert.match(turbo, /MotionEvent\.ACTION_DOWN -> \{[\s\S]*performControllerHaptic\(v\)/);
});
