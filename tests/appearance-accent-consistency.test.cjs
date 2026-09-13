const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const html = read('app/src/main/assets/retra/index.html');
const css = read('app/src/main/assets/retra/style-appearance-accent.css');
const js = read('app/src/main/assets/retra/settings-ui.js');
const core = read('app/src/main/assets/retra/style-core.css');

test('Appearance accent layer is loaded after motion/polish styles', () => {
  const motion = html.indexOf('style-motion-performance.css');
  const accent = html.indexOf('style-appearance-accent.css');
  assert.ok(motion >= 0 && accent > motion);
});

test('Current Appearance theme has a universal selected badge and accessible state', () => {
  assert.match(css, /\.theme-preview-card\.active \.theme-preview-shell::after/);
  assert.match(css, /content:"✓"/);
  assert.match(js, /btn\.setAttribute\('aria-pressed', active \? 'true' : 'false'\)/);
  assert.match(js, /centerThemeCard\(currentThemeCard, 'auto'\)/);
  assert.doesNotMatch(html, /theme-preview-card active" data-theme-name="Tako"/);
});

test('Checked switches and primary controls use the selected Appearance accent', () => {
  assert.match(css, /\.switch input:checked \+ \.slider\{[\s\S]*background:var\(--accent\)/);
  assert.match(css, /input\[type="range"\][\s\S]*accent-color:var\(--accent\)/);
  assert.match(css, /\.modal-btn\.primary,[\s\S]*\.backup-create-button,[\s\S]*\.editor-pill\.primary/);
  assert.match(css, /\.sheet-tab\.active[\s\S]*color:var\(--accent\)/);
});

test('Default Appearance palette uses the blue reference accent rather than legacy orange', () => {
  assert.match(core, /body\[data-theme="default"\][\s\S]*--accent:#8db6ff/);
});
