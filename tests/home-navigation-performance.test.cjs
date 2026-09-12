const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const script = readWebJs(root);
const css = readWebCss(root);
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');

test('main tabs preserve cached page state instead of resetting Home', () => {
  assert.match(script, /capturePageUiState\(previousPageId\)/);
  assert.match(script, /restorePageUiState\(pageId\)/);
  assert.match(script, /state\.scrollTop/);
  assert.match(script, /state\.category/);
  assert.match(script, /state\.searchQuery/);
});

test('Home ROM grid skips unchanged rebuilds and image redecoding', () => {
  assert.match(script, /lastLibraryRenderSignature/);
  assert.match(script, /signature === lastLibraryRenderSignature/);
  assert.match(script, /libraryGrid\.replaceChildren\(fragment\)/);
  assert.match(script, /img\.decoding = 'async'/);
  assert.match(script, /img\.loading = 'lazy'/);
});

test('main tabs do not fade from an empty frame', () => {
  assert.match(css, /#libraryPage\.page,#historyPage\.page,#morePage\.page\{animation:none\}/);
});

test('native gameplay keeps WebView warm and reveals it before hiding overlay', () => {
  assert.match(main, /showWebUiWithoutBlankFrame/);
  assert.match(main, /binding\.webView\.visibility = View\.VISIBLE/);
  assert.match(main, /binding\.webView\.postOnAnimation/);
  assert.match(main, /binding\.emulatorOverlay\.visibility = View\.GONE/);
  assert.match(main, /showEmulatorUiKeepingWebWarm/);
  assert.match(main, /binding\.webView\.visibility = View\.INVISIBLE/);
});
