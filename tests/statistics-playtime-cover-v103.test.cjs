const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const libraryUi = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/library-ui.js'), 'utf8');
const appShell = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/app-shell.js'), 'utf8');
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-core.css'), 'utf8');

test('Statistics Playtime by ROM resolves the same persisted cover source as Library', () => {
  assert.match(libraryUi, /const cover = getNativeRomMediaUrl\(rom\.id, 'cover'\) \|\| romCoverObjectUrls\.get\(rom\.id\) \|\| rom\.cover/);
  assert.match(libraryUi, /async function hydratePlaytimeCover\(row, rom, nativeAlreadyChecked = false\)/);
  assert.match(libraryUi, /getNativeRomMediaUrl\(rom\.id, 'cover'\).*romCoverObjectUrls/s);
});

test('Changing a ROM cover updates an already-open Playtime by ROM row', () => {
  assert.match(appShell, /#playtimeList \.playtime-item/);
  assert.match(appShell, /dataset\?\.playtimeRom/);
  assert.match(appShell, /statisticsImage\.src = url/);
});

test('Playtime cover artwork keeps a compact portrait cover shape', () => {
  assert.match(css, /\.playtime-cover\{[\s\S]*?width: 42px;[\s\S]*?height: 58px;/);
  assert.match(css, /\.playtime-cover img\{[\s\S]*?object-fit: cover;/);
});
