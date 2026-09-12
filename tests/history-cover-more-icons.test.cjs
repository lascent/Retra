const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const libraryUi = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/library-ui.js'), 'utf8');
const appShell = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/app-shell.js'), 'utf8');
const indexHtml = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');

test('History resolves native persisted cover artwork just like Library', () => {
  assert.match(libraryUi, /getNativeRomMediaUrl\(rom\.id, 'cover'\).*romCoverObjectUrls/s);
  assert.match(libraryUi, /const initialCover = rom \? \(getNativeRomMediaUrl\(rom\.id, 'cover'\)/);
});

test('cover updates propagate into an already-open History row', () => {
  assert.match(appShell, /#historyList \.history-item/);
  assert.match(appShell, /historyImage\.src = url/);
  assert.match(appShell, /coverWrap\.classList\.remove\('no-cover'\)/);
});

test('More page uses recognizable Statistics and Settings icon geometry', () => {
  const statistics = indexHtml.match(/data-more-open="statisticsPage"[\s\S]*?<div class="more-copy"><strong>Statistics<\/strong>/)?.[0] || '';
  const settings = indexHtml.match(/data-more-open="settingsHomePage"[\s\S]*?<div class="more-copy"><strong>Settings<\/strong>/)?.[0] || '';
  assert.match(statistics, /M5 20V11/);
  assert.match(statistics, /M12 20V4/);
  assert.match(settings, /<circle cx="12" cy="12" r="3"><\/circle>/);
});


test('More page uses a smooth Color Style palette outline with no top seam', () => {
  const colorStyle = indexHtml.match(/data-more-open="colorStylePage"[\s\S]*?<div class="more-copy"><strong>Color Style<\/strong>/)?.[0] || '';
  assert.match(colorStyle, /M12 2a10 10 0 0 0 0 20/);
  assert.match(colorStyle, /C22 5\.8 17\.52 2 12 2Z/);
  assert.doesNotMatch(colorStyle, /h-3Z/);
  assert.match(colorStyle, /cx="7\.5" cy="10"/);
  assert.match(colorStyle, /cx="10" cy="6\.8"/);
});
