const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const insetsManager = read('app/src/main/java/com/retra/emulator/WebUiInsetsManager.kt');
const script = readWebJs(root);
const css = readWebCss(root);
const html = read('app/src/main/assets/retra/index.html');

test('native Android navigation inset is forwarded to the WebView in CSS pixels', () => {
  assert.match(insetsManager, /WindowInsetsCompat\.Type\.navigationBars\(\)/);
  assert.match(insetsManager, /bottomPhysicalPx \/ density/);
  assert.match(insetsManager, /window\.retraSetNativeBottomInset/);
  assert.match(activity, /WebUiInsetsManager\(binding\.root, binding\.webView\)/);
  assert.match(activity, /webUiInsetsManager\.onPageReady\(\)/);
});

test('web safe-area logic keeps native inset as the minimum bottom obstruction', () => {
  assert.match(script, /let nativeBottomSystemInset = 0/);
  assert.match(script, /window\.retraSetNativeBottomInset = value =>/);
  assert.match(script, /let bottomInset = nativeBottomSystemInset/);
  assert.match(script, /Math\.max\(bottomInset, Math\.min\(32, Math\.round\(obstruction\)\)\)/);
});

test('subpages reserve scrollable space above Android system navigation', () => {
  assert.match(css, /body\.subpage-open main\{[\s\S]*padding-bottom:calc\(42px \+ var\(--device-safe-bottom\)\);/);
  assert.match(css, /scroll-padding-bottom:calc\(42px \+ var\(--device-safe-bottom\)\);/);
  assert.match(css, /\.settings-detail-page\.active\{[\s\S]*padding-bottom:calc\(28px \+ var\(--device-safe-bottom\)\);/);
  assert.match(css, /-webkit-overflow-scrolling:touch/);
});

test('affected Settings actions remain present as normal final rows', () => {
  assert.match(html, /id="openAppFolderBtn"[\s\S]*<strong>Open app folder<\/strong>/);
  assert.match(html, /data-action="Reset advanced settings"[\s\S]*<strong>Reset advanced settings<\/strong>/);
});
