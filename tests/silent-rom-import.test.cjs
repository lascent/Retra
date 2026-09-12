const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const mainActivity = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const appShell = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/app-shell.js'), 'utf8');
const libraryUi = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/library-ui.js'), 'utf8');

test('successful Android ROM imports do not show a completion notice', () => {
  assert.doesNotMatch(mainActivity, /\$imported file\$\{if \(imported == 1\)/);
  assert.doesNotMatch(mainActivity, /\$imported added, \$failed skipped/);
  assert.match(mainActivity, /Could not import .*RetraNotice/s);
});

test('native-to-WebView ROM import updates the library silently', () => {
  const callback = appShell.match(/window\.retraNativeFileImported = function\(metaJson\)\{[\s\S]*?\n\};/)?.[0] || '';
  assert.match(callback, /renderLibraryFromStorage\(\)/);
  assert.match(callback, /lookupAutomaticRomCover/);
  assert.doesNotMatch(callback, /showToast\([^\n]*(?:added|restored with existing data|already in Library)/);
  assert.match(callback, /showToast\('Could not add imported file'\)/);
});

test('fallback ROM importer is silent on normal success but keeps warnings', () => {
  const importer = libraryUi.match(/async function importRomFiles\(fileList\)\{[\s\S]*?\n\}/)?.[0] || '';
  assert.doesNotMatch(importer, /added to Library/);
  assert.match(importer, /ROM storage is unavailable/);
  assert.match(importer, /already in your Library/);
  assert.match(importer, /Unsupported ROM file type/);
});
