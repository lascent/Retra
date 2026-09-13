const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');
const css = readWebCss(path.join(__dirname, '..'));
const safeCss = fs.readFileSync(path.join(__dirname, '..', 'app/src/main/assets/retra/style-safe-insets.css'), 'utf8');

test('ROM detail hero uses responsive safe-area top spacing when opened', () => {
  assert.match(css, /body\.rom-detail-open \.rom-hero\s*\{[\s\S]*padding-top:calc\(var\(--device-safe-top\) \+ clamp\(8px, 2\.5vw, 12px\)\);/);
  assert.match(css, /@media \(max-width:470px\)\{[\s\S]*body\.rom-detail-open \.rom-hero\s*\{[\s\S]*padding-top:calc\(var\(--device-safe-top\) \+ clamp\(8px, 2\.8vw, 10px\)\);/);
});


test('ROM detail parent does not consume the top inset a second time', () => {
  assert.match(safeCss, /body\.subpage-open\.rom-detail-open main\s*\{[\s\S]*padding-top:\s*0;/);
});

test('Android ROM detail stays locked to the viewport and cannot horizontally pan', () => {
  assert.match(safeCss, /body\.rom-detail-open main\s*\{[\s\S]*overflow-x:\s*hidden;[\s\S]*overscroll-behavior-x:\s*none;/);
  assert.match(safeCss, /body\.rom-detail-open \.rom-detail-page\s*\{[\s\S]*width:\s*100%;[\s\S]*max-width:\s*100%;[\s\S]*margin-left:\s*0;[\s\S]*margin-right:\s*0;/);
});
