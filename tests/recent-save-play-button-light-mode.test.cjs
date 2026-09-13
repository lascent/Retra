const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-appearance-light.css'), 'utf8');

test('Light mode gives recent-save play buttons a visible accent-aware background', () => {
  assert.match(css, /body\[data-resolved-mode=\"light\"\] #detailEntries \.entry-download\{/);
  assert.match(css, /background:color-mix\(in srgb, var\(--accent-soft\) 78%, var\(--light-surface,#fff\) 22%\)/);
  assert.match(css, /color:var\(--accent\);/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] #detailEntries \.entry-download:hover/);
});
