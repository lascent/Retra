const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
const html = read('app/src/main/assets/retra/index.html');
const js = read('app/src/main/assets/retra/settings-ui.js');

test('soft controller sounds are independently configurable and portable', () => {
  assert.match(activity, /CONTROLLER_SOUND_PREF = "controller_sound_v1"/);
  assert.match(settings, /put\("controllerSound", prefs\.getBoolean\(CONTROLLER_SOUND_PREF, true\)\)/);
  assert.match(settings, /"controllerSound" -> editor\.putBoolean\(CONTROLLER_SOUND_PREF, value\.toBoolean\(\)\)/);
  assert.match(prefs, /"controller_sound_v1"/);
  assert.match(html, /id="controllerSoundToggle"/);
  assert.match(js, /controllerSoundToggle/);
  assert.match(js, /'controllerSound'/);
});

test('all gameplay controls share the same light vibration and sound profile', () => {
  assert.match(touch, /if \(nextMask != 0\) performControllerHaptic\(binding\.dpadContainer, directional = true\)/);
  assert.match(touch, /performUnifiedControllerHaptic\(view: View, force: Boolean = false\)/);
  assert.match(touch, /!force && now - lastControllerHapticAtMs < 20L/);
  assert.match(touch, /val amplitude = if \(vibrator\.hasAmplitudeControl\(\)\) 150 else VibrationEffect\.DEFAULT_AMPLITUDE/);
  assert.match(touch, /VibrationEffect\.createOneShot\(16L, amplitude\)/);
  assert.match(touch, /performDpadPressFeedback\(view\)/);
  assert.match(touch, /performUnifiedControllerHaptic\(view, force = true\)/);
  assert.match(touch, /if \(nextPressed\) performControllerHaptic\(v\)/);
  assert.match(touch, /performControllerSound\(view\)/);
  assert.match(touch, /now - lastControllerSoundAtMs < 28L/);
  assert.match(touch, /audio\.playSoundEffect\(SoundEffectConstants\.CLICK, 0\.060f\)/);
  assert.doesNotMatch(touch, /val minGapMs = if \(directional\)/);
  assert.doesNotMatch(touch, /val volume = if \(directional\)/);
});
