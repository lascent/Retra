const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const settings = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/settings-ui.js'), 'utf8');
const repo = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/ShaderRepository.kt'), 'utf8');

test('GLSL picker ships with None only and no unowned built-in presets', () => {
  assert.match(settings, /let availableGlslShaders = \[\s*\{ id: 'none', label: 'None', builtIn: true \}\s*\];/s);
  assert.doesNotMatch(settings, /LCD Grid|CRT Lite|Grayscale|lcd-grid|crt-lite/);
  assert.match(repo, /private val BUILT_INS = listOf\(\s*"none" to "None"\s*\)/s);
  assert.doesNotMatch(repo, /LCD_GRID_SHADER|CRT_LITE_SHADER|GRAYSCALE_SHADER|lcd-grid|crt-lite|grayscale/);
});

test('stale shader selections from development builds migrate safely to None', () => {
  assert.match(repo, /Older development builds exposed built-in shader IDs/);
  assert.match(repo, /prefs\.edit\(\)\.putString\(SELECTED_PREF, "none"\)\.apply\(\)/);
});
