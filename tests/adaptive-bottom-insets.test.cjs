const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = p => fs.readFileSync(p, 'utf8');
const html = read('app/src/main/assets/retra/index.html');
const core = read('app/src/main/assets/retra/style-core.css');
const safe = read('app/src/main/assets/retra/style-safe-insets.css');
const categories = read('app/src/main/assets/retra/categories-ui.js');
const manager = read('app/src/main/java/com/retra/emulator/WebUiInsetsManager.kt');

test('adaptive safe-area stylesheet loads last and centralizes native safe variables', () => {
  const safePos = html.indexOf('style-safe-insets.css');
  const polishPos = html.indexOf('ui-polish.css');
  assert.ok(safePos > polishPos);
  for (const key of ['top','right','bottom','left']) {
    assert.match(core, new RegExp(`--device-safe-${key}: max\\(env\\(safe-area-inset-${key}, 0px\\), var\\(--device-${key}-ui\\)\\)`));
  }
});

test('main bottom navigation consumes the dynamic safe area rather than a device-sized gap', () => {
  assert.match(safe, /\.bottom-nav\s*\{[\s\S]*calc\(12px \+ var\(--device-safe-bottom\)\)/);
  assert.match(core, /--bottom-nav-total-height:\s*calc\(var\(--bottom-nav-height\) \+ var\(--device-safe-bottom\)\)/);
  assert.doesNotMatch(categories, /innerHeight\s*\*/);
});

test('ROM category sheet keeps actions outside the scroll body and applies bottom inset only to footer', () => {
  const contentStart = html.indexOf('<div class="sheet-content">');
  const contentEnd = html.indexOf('</div>\n<div class="sheet-actions">', contentStart);
  assert.ok(contentStart >= 0 && contentEnd > contentStart);
  assert.match(safe, /#romFilterSheetBackdrop \.sheet-content\s*\{[\s\S]*padding-bottom:\s*4px/);
  assert.match(safe, /#romFilterSheetBackdrop \.sheet-actions\s*\{[\s\S]*calc\(12px \+ var\(--device-safe-bottom\)\)/);
  assert.match(safe, /#romFilterSheetBackdrop \.rom-sheet\s*\{[\s\S]*min-height:\s*0/);
});

test('native bridge excludes IME and updates on real system bars and display cutouts', () => {
  assert.match(manager, /Type\.systemBars\(\) or WindowInsetsCompat\.Type\.displayCutout\(\)/);
  assert.doesNotMatch(manager, /Type\.ime\(\)/);
  assert.match(manager, /ViewCompat\.requestApplyInsets\(rootView\)/);
});

test('native Android runtime uses the full app window instead of the 428px preview shell', () => {
  assert.match(categories, /nativeInsetsConnected\s*=\s*true/);
  assert.match(categories, /root\.dataset\.retraRuntime\s*=\s*['"]android['"]/);
  assert.match(safe, /html\[data-retra-runtime="android"\] \.app-shell\s*\{[\s\S]*width:\s*100vw[\s\S]*height:\s*100dvh[\s\S]*max-width:\s*none/);
  assert.match(safe, /orientation:\s*landscape[\s\S]*data-retra-runtime="android"[\s\S]*max-width:\s*none/);
});

test('native adaptive layer protects horizontal cutouts and headerless top content', () => {
  assert.match(safe, /html\[data-retra-runtime="android"\] main\s*\{[\s\S]*var\(--device-safe-left\)[\s\S]*var\(--device-safe-right\)/);
  assert.match(safe, /body\.more-headerless main\s*\{[\s\S]*var\(--device-safe-top\)/);
});

test('Android host is explicitly resizable and uses IME resize behavior', () => {
  const manifest = read('app/src/main/AndroidManifest.xml');
  assert.match(manifest, /android:resizeableActivity="true"/);
  assert.match(manifest, /android:windowSoftInputMode="adjustResize"/);
  assert.match(manifest, /smallestScreenSize/);
  assert.match(manifest, /screenLayout/);
});

