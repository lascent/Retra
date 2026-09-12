const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/ui-polish.css'), 'utf8');

test('Refresh library popup uses an opaque themed background', () => {
  assert.match(html, /id="refreshLibraryBtn"/);
  assert.match(css, /\.library-overflow-menu\s*\{[\s\S]*background:\s*var\(--panel-2\)\s*!important;[\s\S]*backdrop-filter:\s*none\s*!important;/);
  assert.match(css, /\.library-overflow-item\s*\{[\s\S]*background:\s*var\(--panel\)\s*!important;/);
});
