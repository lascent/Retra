const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const settings = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/settings-ui.js'), 'utf8');
const repo = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/ShaderRepository.kt'), 'utf8');

test('Retra ships a curated built-in shader pack with Off as the zero-cost default', () => {
  for (const label of [
    'GBA Color Corrected',
    'Sharp',
    'Smooth',
    'Pixel Perfect',
    'LCD Grid',
    'LCD Response',
    'Scanlines',
    'CRT Lite',
    'Retro Warm'
  ]) {
    assert.match(repo, new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    assert.match(settings, new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.match(repo, /id = "none"[\s\S]*label = "Off"[\s\S]*impact = "None"/);
  assert.match(settings, /Off • no extra GPU cost/);
  assert.match(repo, /BUILT_IN_BY_ID\.containsKey\(stored\)/);
});

test('unknown or stale shader selections migrate safely to Off', () => {
  assert.match(repo, /Unknown\/stale selections are intentionally reset/);
  assert.match(repo, /prefs\.edit\(\)\.putString\(SELECTED_PREF, "none"\)\.apply\(\)/);
});
