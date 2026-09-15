const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const touch = read('app/src/main/java/com/retra/emulator/GameplayTouchController.kt');
const gameplay = read('app/src/main/java/com/retra/emulator/GameplayController.kt');
const shell = read('app/src/main/assets/retra/app-shell.js');
const html = read('app/src/main/assets/retra/index.html');
const readme = read('README.md');

test('controller, D-pad and menu taps use one stronger smooth haptic profile', () => {
  assert.match(touch, /createOneShot\(16L, amplitude\)/);
  assert.match(touch, /hasAmplitudeControl\(\)\) 150/);
  assert.match(touch, /lastControllerHapticAtMs < 20L/);
  assert.match(touch, /performUiTapHaptic\(view: View/);
  assert.match(activity, /fun performUiHaptic\(\)/);
  assert.match(activity, /performUiTapHaptic\(binding\.webView\)/);
  assert.match(shell, /AndroidBridge\.performUiHaptic\(\)/);
  assert.match(shell, /document\.addEventListener\('pointerdown'/);
  assert.match(gameplay, /performUiTapHaptic\(tapped\)/);
  assert.match(html, /Haptic feedback/);
  assert.match(html, /controller buttons, D-pad and menu taps/);
});

test('README no longer advertises RFU as an unsupported v1.0.3 item', () => {
  assert.doesNotMatch(readme, /### Not supported in v1\.0\.3/);
  assert.doesNotMatch(readme, /GBA Wireless Adapter \/ RFU emulation/);
});
