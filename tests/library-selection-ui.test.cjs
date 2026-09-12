const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');
const script = readWebJs(root);
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/ui-polish.css'), 'utf8');

test('bulk More menu only removes ROMs from Library, never deletes game data', () => {
  const start = html.indexOf('id="librarySelectionMoreMenu"');
  const end = html.indexOf('<!-- MULTI-SELECT CATEGORY ASSIGNMENT -->');
  const menu = html.slice(start, end);
  assert.match(menu, /id="librarySelectionRemove"/);
  assert.doesNotMatch(menu, /librarySelectionDeleteData|Delete Game Data/);
});

test('bulk favourite action exits selection mode after applying the change', () => {
  const start = script.indexOf("librarySelectionFavorite?.addEventListener('click'");
  const end = script.indexOf("librarySelectionMore?.addEventListener('click'", start);
  const handler = script.slice(start, end);
  assert.match(handler, /saveLibraryRoms\(\);[\s\S]*exitLibrarySelection\(\);[\s\S]*renderLibraryFromStorage\(\);/);
});


test('saving multi-select categories exits selection mode automatically', () => {
  const start = script.indexOf('function saveMultiSelectedCategories()');
  const end = script.indexOf('function saveCategories()', start);
  const handler = script.slice(start, end);
  assert.match(handler, /const selectedCount = selected\.length;/);
  assert.match(handler, /closeMultiCategoryModal\(\);[\s\S]*exitLibrarySelection\(\);[\s\S]*Categories updated for \${selectedCount}/);
});

test('selection actions are centered and larger for touch use', () => {
  assert.match(css, /\.library-selection-actions\s*\{[\s\S]*left:\s*50%;[\s\S]*width:\s*min\(372px,[\s\S]*transform:\s*translateX\(-50%\)/);
  assert.match(css, /\.library-selection-action\s*\{[\s\S]*height:\s*56px/);
  assert.match(css, /\.library-selection-action svg\s*\{\s*width:\s*23px;\s*height:\s*23px/);
});

test('additional selections patch only the changed rendered card', () => {
  assert.match(script, /renderedLibraryCards\.get\(String\(changedId\)\)/);
  assert.match(script, /patchLibrarySelectionCardImmediately\(key, card\);/);
  assert.match(script, /updateLibrarySelectionUi\(\{ changedId: key, wasActive \}\)/);
  assert.match(script, /if \(isLibrarySelectionMode\(\)\) return;/);
});


test('long-press click suppression is scoped to the pressed ROM only', () => {
  assert.match(script, /let suppressedLibraryCardClickId = null;/);
  assert.match(script, /function suppressLibraryCardClickFor\(id/);
  assert.match(script, /if \(shouldSuppressLibraryCardClick\(rom\.id\)\)/);
  assert.doesNotMatch(script, /if \(Date\.now\(\) < suppressLibraryCardClickUntil\)/);
});

test('bulk More popup sits lower, stays compact and uses a solid themed background', () => {
  assert.match(css, /\.library-selection-more-menu\s*\{[\s\S]*right:\s*max\(calc\(env\(safe-area-inset-right, 0px\) \+ 12px\), clamp\(16px, 7vw, 28px\)\);[\s\S]*bottom:\s*calc\(86px \+ var\(--device-safe-bottom\)\);[\s\S]*z-index:\s*180;[\s\S]*width:\s*min\(184px, calc\(100vw - 38px\)\);[\s\S]*background:\s*linear-gradient\(180deg,/);
  assert.match(css, /\.library-selection-more-menu button\s*\{[\s\S]*min-height:\s*44px/);
});


test('selected cards keep a redundant visual state across re-renders', () => {
  assert.match(script, /card\.dataset\.selected = chosen \? 'true' : 'false'/);
  assert.match(script, /applyLibrarySelectionCardState\(article, isLibrarySelectionMode\(\)\)/);
  assert.match(css, /\.cover-card\[data-selected="true"\]/);
});

test('multi-select click handlers pass the rendered card for immediate painting', () => {
  assert.match(script, /toggleLibraryRomSelection\(rom\.id, article\)/);
  assert.match(script, /function patchLibrarySelectionCardImmediately\(id, card = null\)/);
});
