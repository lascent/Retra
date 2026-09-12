const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');
const css = readWebCss(path.join(__dirname, '..'));

test('ROM detail hero uses responsive safe-area top spacing when opened', () => {
  assert.match(css, /body\.rom-detail-open \.rom-hero\s*\{[\s\S]*padding-top:calc\(env\(safe-area-inset-top, 0px\) \+ clamp\(18px, 4\.8vw, 30px\)\);/);
  assert.match(css, /@media \(max-width:470px\)\{[\s\S]*body\.rom-detail-open \.rom-hero\s*\{[\s\S]*padding-top:calc\(env\(safe-area-inset-top, 0px\) \+ clamp\(16px, 5\.2vw, 24px\)\);/);
});
