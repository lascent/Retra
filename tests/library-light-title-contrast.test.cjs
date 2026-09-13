const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-appearance-light.css'), 'utf8');

test('Light mode keeps ROM card titles readable on bright artwork', () => {
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.cover-card::after/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.cover-info strong\{[\s\S]*color:#ffffff;/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.cover-info span\{[\s\S]*rgba\(255,255,255,\.84\)/);
});
