const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-core.css'), 'utf8');
const js = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/library-ui.js'), 'utf8');

test('ROM detail hides the filename-size row and moves genre chips up', () => {
  assert.match(css, /\.rom-description\{[\s\S]*display:none;/);
  assert.match(css, /\.chip-row\{[\s\S]*margin-top:-4px;/);
  assert.match(js, /detailDescription\.textContent = '';/);
  assert.match(js, /detailDescription\.hidden = true;/);
});
