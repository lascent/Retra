const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-appearance-light.css'), 'utf8');

test('Light mode keeps Local ROM / genre chips visibly separated from the page', () => {
  assert.match(css, /body\[data-resolved-mode="light"\] \.rom-detail-body \.genre-chip\{/);
  assert.match(css, /var\(--light-surface-2,#f1f3f7\) 78%/);
  assert.match(css, /var\(--accent\) 22%/);
  assert.match(css, /border-color:color-mix\(in srgb, var\(--accent\) 28%, var\(--text\) 8%\)/);
  assert.match(css, /backdrop-filter:none/);
});
