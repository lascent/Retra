const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const polish = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/ui-polish.css'), 'utf8');

test('More page Retra logo has deliberate lower top spacing', () => {
  assert.match(polish, /#morePage \.more-logo-wrap\s*\{[\s\S]*?padding-top:\s*16px;/);
});

test('both About pages position the Retra logo slightly lower', () => {
  assert.match(polish, /#aboutSettingsPage \.about-panel,\s*#aboutPage \.about-panel\s*\{[\s\S]*?padding-top:\s*34px;/);
});
