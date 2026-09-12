const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');
const css = readWebCss(root);

test('category and confirm dialogs use shared modal title/copy structure', () => {
  assert.match(html, /<h3 class="modal-title">Add category<\/h3>/);
  assert.match(html, /<p class="modal-copy">Create a category for organizing ROMs in your Library\.<\/p>/);
  assert.match(html, /<h3 class="modal-title" id="confirmTitle">Confirm<\/h3>/);
  assert.match(html, /<p class="modal-copy" id="confirmMessage">Are you sure\?<\/p>/);
});

test('confirm and category dialogs share the same rounded card system', () => {
  assert.match(css, /\.category-modal-card,\s*\n\.confirm-modal-card\s*\{[\s\S]*border-radius:24px;[\s\S]*background:linear-gradient\(180deg, rgba\(24,26,40,.97\) 0%, rgba\(18,20,34,.97\) 100%\);/);
  assert.match(css, /\.modal-actions,\s*\n\.confirm-actions\s*\{[\s\S]*grid-template-columns:repeat\(2, minmax\(0, 1fr\)\);/);
  assert.match(css, /\.modal-btn\s*\{[\s\S]*min-height:46px;[\s\S]*border-radius:14px;[\s\S]*font-size:15px;/);
});

test('category input uses the polished dialog input style', () => {
  assert.match(css, /\.category-modal-card input\s*\{[\s\S]*min-height:50px;[\s\S]*border-radius:15px;[\s\S]*background:rgba\(255,255,255,.04\);/);
  assert.match(css, /\.category-modal-card input:focus\s*\{[\s\S]*box-shadow:0 0 0 3px rgba\(228,174,107,.14\)/);
});
