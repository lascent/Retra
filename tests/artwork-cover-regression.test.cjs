const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const shell = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/app-shell.js'), 'utf8');
const library = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/library-ui.js'), 'utf8');
const categories = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/categories-ui.js'), 'utf8');
const index = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');

test('shared cover renderer exists before artwork and cover-picker modules use it', () => {
  assert.match(shell, /function applyCoverToRenderedRom\(rom, coverUrl, card = null\)/);
  assert.match(shell, /targetCard\.dataset\.cover = url/);
  assert.match(shell, /targetCard\.classList\.remove\('no-cover'\)/);
  assert.match(shell, /detailCover\.src = url/);
  assert.ok(index.indexOf('app-shell.js') < index.indexOf('library-ui.js'));
  assert.ok(index.indexOf('app-shell.js') < index.indexOf('categories-ui.js'));
});

test('native automatic artwork callback applies downloaded cover through shared renderer', () => {
  assert.match(library, /window\.retraNativeArtworkChanged = function/);
  assert.match(library, /applyCoverToRenderedRom\(rom, coverUrl, card\)/);
});

test('manual cover save has a display fallback when native media URL is not immediately visible', () => {
  assert.match(categories, /getNativeRomMediaUrl\(rom\.id, 'cover'\) \|\| dataUrl/);
  assert.match(categories, /applyCoverToRenderedRom\(rom, coverUrl, currentRomCard\)/);
});
