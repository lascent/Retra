const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const html = read('app/src/main/assets/retra/index.html');
const appShell = read('app/src/main/assets/retra/app-shell.js');
const libraryUi = read('app/src/main/assets/retra/library-ui.js');
const css = read('app/src/main/assets/retra/ui-polish.css');

test('Library header exposes Search and one icon-only Sort/Display control without adding a Filter button', () => {
  const header = html.match(/<header class="main-header active-header" id="libraryHeader">[\s\S]*?<\/header>/)?.[0] || '';
  assert.match(header, /id="searchBtn"/);
  assert.match(header, /id="libraryViewBtn"/);
  assert.match(header, /aria-label="Library sort and display"/);
  assert.doesNotMatch(header, /id="librarySortBtn"/);
  assert.doesNotMatch(header, /id="libraryDisplayBtn"/);
  assert.doesNotMatch(header, /id="libraryFilterBtn"/);
});

test('Library Display sheet contains only display mode and items-per-row controls', () => {
  const sheet = html.match(/<!-- LIBRARY SORT \/ DISPLAY SHEET -->[\s\S]*?<nav class="bottom-nav">/)?.[0] || '';
  assert.match(sheet, />Sort<\/button>/);
  assert.match(sheet, />Display<\/button>/);
  assert.match(sheet, /Compact grid/);
  assert.match(sheet, /Comfortable grid/);
  assert.match(sheet, /Cover-only grid/);
  assert.match(sheet, />List<\/button>/);
  assert.match(sheet, /Items per row/);
  assert.doesNotMatch(sheet, />Filter<\/button>/);
  assert.doesNotMatch(sheet, />Overlay</);
  assert.doesNotMatch(sheet, />Tabs</);
});

test('Library sort persists and supports alphabetical, playtime, last played, date added and random', () => {
  assert.match(appShell, /const libraryViewStorageKey = 'retraLibraryViewV1'/);
  assert.match(appShell, /allowedSort = new Set\(\['alphabetical', 'playtime', 'lastPlayed', 'dateAdded', 'random'\]\)/);
  assert.match(appShell, /readLibraryPlaytimeSortMap\(\)/);
  assert.match(appShell, /lastPlayedByRom/);
  assert.match(appShell, /stableLibraryRandomRank/);
  assert.match(libraryUi, /refreshLibraryRandomSeed\(\)/);
  assert.match(libraryUi, /renderLibraryFromStorage\(\{ force: true \}\)/);
});

test('Library display preference persists all four modes and a 2-6 items-per-row range', () => {
  assert.match(appShell, /allowedDisplay = new Set\(\['compact', 'comfortable', 'coverOnly', 'list'\]\)/);
  assert.match(appShell, /Math\.min\(6, Math\.max\(2,/);
  assert.match(libraryUi, /library-display-compact/);
  assert.match(libraryUi, /library-display-comfortable/);
  assert.match(libraryUi, /library-display-cover-only/);
  assert.match(libraryUi, /library-display-list/);
  assert.match(libraryUi, /setProperty\('--library-columns'/);
  assert.match(css, /grid-template-columns:repeat\(var\(--library-columns, 3\)/);
});

test('Compact, cover-only and list modes have distinct rendering behavior', () => {
  assert.match(css, /library-display-compact \.cover-info\{[\s\S]*?position:static/);
  assert.match(css, /library-display-cover-only \.cover-info,[\s\S]*?display:none !important/);
  assert.match(css, /library-display-list\{[\s\S]*?display:flex;[\s\S]*?flex-direction:column/);
  assert.match(css, /library-display-list \.cover-card > img\{[\s\S]*?width:48px;[\s\S]*?height:68px/);
  assert.match(libraryUi, /card\.style\.display = show \? '' : 'none'/);
});


test('single Library Options icon opens the shared Sort/Display sheet', () => {
  assert.match(libraryUi, /const libraryViewBtn = document\.getElementById\('libraryViewBtn'\)/);
  assert.match(libraryUi, /libraryViewBtn\?\.addEventListener\('click'[\s\S]*?openLibraryViewSheet\('sort'\)/);
  assert.doesNotMatch(libraryUi, /librarySortBtn|libraryDisplayBtn/);
});
