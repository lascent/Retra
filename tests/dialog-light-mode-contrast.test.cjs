const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readWebCss } = require('./helpers/assets.cjs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const css = readWebCss(root);

test('Light mode confirmation dialogs keep readable title/body text and a visible cancel button', () => {
  assert.match(css, /body\[data-resolved-mode="light"\] \.category-modal-card \.modal-title,[\s\S]*body\[data-resolved-mode="light"\] \.confirm-modal-card h3\{[\s\S]*color:#1f2538;/);
  assert.match(css, /body\[data-resolved-mode="light"\] \.category-modal-card \.modal-copy,[\s\S]*body\[data-resolved-mode="light"\] \.confirm-modal-card p\{[\s\S]*color:rgba\(44,50,73,.84\);/);
  assert.match(css, /body\[data-resolved-mode="light"\] \.modal-btn\.secondary\{[\s\S]*background:rgba\(255,255,255,.92\);[\s\S]*color:#20263a;/);
  assert.match(css, /body\[data-resolved-mode="light"\]\[data-translucent="true"\] \.confirm-modal-card,[\s\S]*background:linear-gradient\(180deg, rgba\(248,245,248,.98\) 0%, rgba\(241,236,240,.99\) 100%\);/);
});
