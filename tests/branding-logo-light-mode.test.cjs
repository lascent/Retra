const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-appearance-light.css'), 'utf8');

test('More and About Retra logos turn black only in Light mode', () => {
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.more-logo,\s*body\[data-resolved-mode=\"light\"\] \.about-logo/);
  assert.match(css, /filter:brightness\(0\) saturate\(100%\)/);
});
