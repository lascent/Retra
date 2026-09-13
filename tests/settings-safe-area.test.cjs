const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const gameplayLayout = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
const insetsManager = read('app/src/main/java/com/retra/emulator/WebUiInsetsManager.kt');
const script = readWebJs(root);
const css = readWebCss(root);
const html = read('app/src/main/assets/retra/index.html');
const safeCss = read('app/src/main/assets/retra/style-safe-insets.css');

test('native Android safe drawing insets are forwarded to the WebView in CSS pixels', () => {
  assert.match(insetsManager, /WindowInsetsCompat\.Type\.systemBars\(\) or WindowInsetsCompat\.Type\.displayCutout\(\)/);
  assert.match(insetsManager, /safeDrawing\.bottom \/ density/);
  assert.match(insetsManager, /window\.retraSetNativeSafeInsets/);
  assert.match(activity, /WebUiInsetsManager\(window, binding\.root, binding\.webView\)/);
  assert.match(activity, /webUiInsetsManager\.onPageReady\(\)/);
});

test('normal Retra Web UI is edge-to-edge and has one native inset owner', () => {
  assert.match(insetsManager, /fun activateForWebUi\(\)/);
  assert.match(insetsManager, /WindowCompat\.setDecorFitsSystemWindows\(window, false\)/);
  assert.match(insetsManager, /show\(WindowInsetsCompat\.Type\.systemBars\(\)\)/);
  assert.match(gameplayLayout, /webUiInsetsManager\.activateForWebUi\(\)/);
});

test('web safe-area logic uses native values without device or viewport heuristics', () => {
  assert.match(script, /const nativeSafeInsets = \{ top: 0, right: 0, bottom: 0, left: 0 \}/);
  assert.match(script, /window\.retraSetNativeSafeInsets = \(top, right, bottom, left\) =>/);
  assert.match(script, /--device-bottom-ui/);
  assert.doesNotMatch(script, /window\.innerHeight \* 0\.032/);
  assert.doesNotMatch(script, /Math\.min\(30/);
  assert.doesNotMatch(script, /isSmallMobile/);
});

test('subpages consume the system bottom inset once at the outer scrolling main', () => {
  assert.match(css, /body\.subpage-open main\{[\s\S]*padding-bottom:calc\(42px \+ var\(--device-safe-bottom\)\);/);
  assert.match(css, /scroll-padding-bottom:calc\(42px \+ var\(--device-safe-bottom\)\);/);
  assert.match(safeCss, /\.settings-detail-page\.active[\s\S]*padding-bottom:\s*28px/);
  assert.match(css, /-webkit-overflow-scrolling:touch/);
});

test('affected Settings actions remain present as normal final rows', () => {
  assert.match(html, /id="openAppFolderBtn"[\s\S]*<strong>Open app folder<\/strong>/);
  assert.match(html, /data-action="Reset advanced settings"[\s\S]*<strong>Reset advanced settings<\/strong>/);
});
