const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const html = read('app/src/main/assets/retra/index.html');
const css = read('app/src/main/assets/retra/style-appearance-light.css');
const js = read('app/src/main/assets/retra/settings-ui.js');

test('reference-driven Light appearance layer loads after accent consistency styles', () => {
  const accent = html.indexOf('style-appearance-accent.css');
  const light = html.indexOf('style-appearance-light.css');
  const scroll = html.indexOf('style-scroll-performance.css');
  assert.ok(accent >= 0 && light > accent && scroll > light);
});

test('all supplied Mihon-reference palettes have dedicated Retra light variants', () => {
  const themes = [
    'default','dynamic','catppuccin','green-apple','lavender','midnight-dusk',
    'nord','strawberry-daiquiri','tako','teal-turquoise','tidal-wave',
    'yin-yang','yotsuba','tokyo-night','monochrome'
  ];
  for (const theme of themes) {
    assert.ok(css.includes(`body[data-resolved-mode="light"][data-theme="${theme}"]`), theme);
  }
  assert.ok(css.includes('body[data-resolved-mode="light"][data-theme="aurora-mint"]'));
});

test('Light mode updates page surfaces, selected mode segment and preview cards', () => {
  assert.match(css, /--body-start:var\(--light-page/);
  assert.match(css, /--shell-start:var\(--light-page/);
  assert.match(css, /\.theme-mode-option\.active\{[\s\S]*--light-mode-selected-bg/);
  assert.match(css, /body\[data-resolved-mode="light"\] \.theme-preview-shell/);
  assert.match(css, /theme-preview-card\[data-theme-name="Default"\]/);
});

test('System mode still resolves through the same light palette path', () => {
  assert.match(js, /appearanceState\.mode === 'System'/);
  assert.match(js, /systemColorScheme\.matches \? 'light' : 'dark'/);
  assert.match(js, /document\.body\.dataset\.resolvedMode = resolvedMode/);
});
