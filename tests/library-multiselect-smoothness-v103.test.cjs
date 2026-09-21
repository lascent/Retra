const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const script = readWebJs(root);
const css = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/ui-polish.css'), 'utf8');
const libraryUi = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/library-ui.js'), 'utf8');

test('selected ROMs use a reliable curved cover-only border instead of card outline/shadow', () => {
  assert.match(css, /\.cover-card > img,[\s\S]*\.cover-card \.cover-art-placeholder\s*\{[\s\S]*box-sizing:\s*border-box;[\s\S]*border:\s*0 solid transparent/);
  assert.match(css, /\.cover-card\[data-selected="true"\] > img,[\s\S]*\.cover-card\[data-selected="true"\] \.cover-art-placeholder\s*\{[\s\S]*border-width:\s*3px;[\s\S]*border-color:\s*var\(--accent\)/);
  assert.match(css, /library-display-compact \.cover-card\[data-selected="true"\] > img,[\s\S]*border-radius:\s*16px/);
  assert.match(css, /body\.library-selection-mode \.cover-card\[data-selected="true"\]\s*\{[\s\S]*outline:\s*none;[\s\S]*box-shadow:\s*none/);
});

test('touch and pen multi-select toggles on pointer-up and suppresses the synthetic click', () => {
  assert.match(script, /function bindRomCardSelectionTap\(article, rom\)/);
  assert.match(script, /article\.addEventListener\('pointerup',[\s\S]*suppressLibraryCardClickFor\(rom\.id, 360\);[\s\S]*toggleLibraryRomSelection\(rom\.id, article\)/);
  assert.match(libraryUi, /bindRomCardSelectionTap\(article, rom\);[\s\S]*bindRomCardLongPress\(article, rom\);/);
});

test('selection pointer path preserves scrolling by cancelling after movement', () => {
  assert.match(script, /Math\.hypot\(event\.clientX - startX, event\.clientY - startY\) > libraryCardMoveTolerance/);
  assert.match(script, /moved = true;[\s\S]*article\.classList\.remove\('selection-pressing'\)/);
  assert.match(css, /\.cover-card\.selection-pressing\s*\{[\s\S]*transform:\s*scale\(\.982\)/);
});

test('additional selections avoid full stale-ID cleanup and defer accessory bookkeeping until paint', () => {
  const start = script.indexOf('function updateLibrarySelectionUi');
  const end = script.indexOf('function exitLibrarySelection', start);
  const handler = script.slice(start, end);
  assert.match(handler, /if \(changedId === null\) \{[\s\S]*const validIds = new Set/);
  assert.match(handler, /librarySelectionCount\.textContent = `\$\{librarySelectedRomIds\.size\} selected`/);
  assert.match(handler, /scheduleLibrarySelectionAccessoryUi\(\)/);
  assert.match(script, /requestAnimationFrame\(refreshLibrarySelectionAccessoryUi\)/);
});
