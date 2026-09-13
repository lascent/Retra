const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-appearance-light.css'), 'utf8');

test('Light mode keeps ROM detail hero title and metadata readable on artwork', () => {
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.rom-hero-overlay\{/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.rom-meta h2\{[\s\S]*color:#ffffff;/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.meta-line\{[\s\S]*rgba\(255,255,255,\.96\)/);
  assert.match(css, /body\[data-resolved-mode=\"light\"\] \.meta-line svg\{[\s\S]*rgba\(255,255,255,\.92\)/);
});
