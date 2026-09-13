const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-features.css'), 'utf8');
const js = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/library-ui.js'), 'utf8');

test('Recent-save play button keeps its circle size while the glyph is larger, centered, and rounded', () => {
  assert.match(css, /#detailEntries \.entry-download\{[\s\S]*width:42px;[\s\S]*height:42px;[\s\S]*border-radius:50%;/);
  assert.match(css, /#detailEntries \.entry-download \.recent-save-play-icon\{[\s\S]*width:27px;[\s\S]*height:27px;/);
  assert.match(css, /transform:translateX\(-0\.8px\)/);
  assert.match(js, /M8\.7 6\.8c0-1\.02/);
});
