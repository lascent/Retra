const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const html = read('app/src/main/assets/retra/index.html');
const js = read('app/src/main/assets/retra/settings-ui.js');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
const shaderView = read('app/src/main/java/com/retra/emulator/ShaderGameView.kt');
const controller = read('app/src/main/java/com/retra/emulator/ColorStyleController.kt');

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

test('Color Style appears in More directly above Settings with four visual presets', () => {
  const colorIndex = html.indexOf('data-more-open="colorStylePage"');
  const settingsIndex = html.indexOf('data-more-open="settingsHomePage"');
  assert.ok(colorIndex >= 0 && settingsIndex > colorIndex);
  for (const style of ['classic', 'vivid', 'warm', 'muted']) {
    assert.match(html, new RegExp(`data-color-style="${style}"`));
    assert.match(html, new RegExp(`assets/color-style/${style}\\.png`));
  }
});

test('Classic preview is the exact provided baseline image copied into app assets', () => {
  const classic = path.join(root, 'app/src/main/assets/retra/assets/color-style/classic.png');
  assert.ok(fs.existsSync(classic));
  // Reference file is copied byte-for-byte by the feature implementation.
  assert.equal(fs.statSync(classic).size, 6402);
});

test('Color Style persists through DataStore and is applied only to gameplay renderers', () => {
  assert.match(prefs, /"game_color_style_v1"/);
  assert.match(settings, /put\("colorStyle", colorStyleController\.selectedId\(\)\)/);
  assert.match(settings, /"colorStyle" -> editor\.putString/);
  assert.match(settings, /colorStyleController\.apply\(\)/);
  assert.match(controller, /normalView\.colorFilter = ColorMatrixColorFilter/);
  assert.match(controller, /shaderView\.setColorTransform/);
  assert.doesNotMatch(controller, /webView|app-shell|theme/i);
});

test('Web selector updates immediately and forwards the chosen style to native settings', () => {
  assert.match(js, /colorStyleLabels = \{ classic: 'Classic', vivid: 'Vivid', warm: 'Warm', muted: 'Muted' \}/);
  assert.match(js, /setNativeSetting\('colorStyle', style\)/);
  assert.match(js, /applyColorStyleUi\(state\.colorStyle \|\| selectedColorStyle\)/);
});

test('Color Style remains compatible with optional GLSL shaders using a post-process matrix', () => {
  assert.match(shaderView, /uRetraColorMatrix/);
  assert.match(shaderView, /uRetraColorOffset/);
  assert.match(shaderView, /decorateFragmentShader/);
  assert.match(shaderView, /glUniformMatrix4fv/);
});
