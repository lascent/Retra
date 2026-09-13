const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-appearance-light.css'), 'utf8');

test('Light mode uses darker Mihon-style separators without changing dark mode', () => {
  assert.match(css, /--line:color-mix\(in srgb, var\(--text\) 18%, transparent\)/);
  assert.match(css, /#morePage \.more-card\{[\s\S]*?24%/);
  assert.match(css, /#morePage \.more-toggle-row,[\s\S]*?#morePage \.more-item\{[\s\S]*?24%/);
  assert.match(css, /#settingsHomePage \.settings-item\{[\s\S]*?16%/);
  assert.match(css, /\.bottom-nav\{[\s\S]*?20%/);
});
