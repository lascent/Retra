const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const transfer = read('app/src/main/java/com/retra/emulator/SaveTransferRepository.kt');

test('Open app folder always returns through the registered picker before export', () => {
  assert.match(settings, /pendingAppFolderSelection = true/);
  assert.match(settings, /appFolderPicker\.launch\(existing\)/);
  assert.doesNotMatch(settings, /exportSaveDataToTreeAsync\(existing, showResult = false\)/);
  assert.doesNotMatch(settings, /startActivity\(intent\)/);
});

test('portable SAF directories are reused instead of repeatedly creating numbered copies', () => {
  assert.match(transfer, /private fun ensureDirectory\(parent: DocumentFile, name: String\)/);
  assert.match(transfer, /parent\.listFiles\(\)\.toList\(\)/);
  assert.match(transfer, /child\.name == name/);
  assert.match(transfer, /RegexOption\.IGNORE_CASE/);
  assert.match(transfer, /minByOrNull \{ it\.first \}/);
  assert.match(transfer, /dir = ensureDirectory\(dir, segment\)/);
  assert.doesNotMatch(transfer, /dir = dir\.findFile\(segment\)\?\.takeIf \{ it\.isDirectory \}\s*\?: dir\.createDirectory\(segment\)/);
});

test('new save data updates an existing SAF file rather than creating another file', () => {
  assert.match(transfer, /return findExistingFile\(dir, name\)\s*\?: dir\.createFile/);
  assert.match(transfer, /openOutputStream\(document\.uri, "wt"\)/);
  assert.match(transfer, /copyFileToDocumentVerified\(file, document\)/);
});
