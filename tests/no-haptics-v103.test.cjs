const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');

test('v1.0.3 contains no Retra haptic or vibration feature', () => {
  const files = [
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/retra/emulator/MainActivity.kt',
    'app/src/main/java/com/retra/emulator/GameplayTouchController.kt',
    'app/src/main/java/com/retra/emulator/GameplayLayoutController.kt',
    'app/src/main/java/com/retra/emulator/GameplayController.kt',
    'app/src/main/java/com/retra/emulator/SettingsController.kt',
    'app/src/main/java/com/retra/emulator/RetraPreferences.kt',
    'app/src/main/assets/retra/index.html',
    'app/src/main/assets/retra/settings-ui.js',
    'app/src/main/assets/retra/app-shell.js',
    'app/src/main/assets/retra/library-ui.js',
  ];
  const joined = files.map(read).join('\n');
  assert.doesNotMatch(joined, /CONTROLLER_HAPTICS|controllerHaptics|controller_haptics|performUiHaptic|performUiTapHaptic|performControllerHaptic|performUnifiedControllerHaptic|VibrationEffect|VibratorManager|navigator\.vibrate|android\.permission\.VIBRATE|Haptic feedback|Sound & Haptics/);
});

test('controller sound remains available without vibration', () => {
  const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
  const html = read('app/src/main/assets/retra/index.html');
  assert.match(touch, /performControllerSound/);
  assert.match(html, /id="controllerSoundToggle"/);
  assert.match(html, /<h2>Sound<\/h2>/);
});
