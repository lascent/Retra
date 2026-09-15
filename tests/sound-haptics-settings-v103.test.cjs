const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');

test('controller sound and vibration live under Sound & Haptics, not Misc', () => {
  assert.match(html, /<strong>Sound &amp; Haptics<\/strong><span>Game audio, controller sound, vibration<\/span>/);
  const audio = html.slice(html.indexOf('id="audioSettingsPage"'), html.indexOf('id="layoutsSettingsPage"'));
  const misc = html.slice(html.indexOf('id="miscSettingsPage"'), html.indexOf('id="advancedSettingsPage"'));
  assert.match(audio, /<h2>Sound &amp; Haptics<\/h2>/);
  assert.match(audio, /id="controllerSoundToggle"/);
  assert.match(audio, /id="controllerHapticsToggle"/);
  assert.doesNotMatch(misc, /id="controllerSoundToggle"/);
  assert.doesNotMatch(misc, /id="controllerHapticsToggle"/);
});
