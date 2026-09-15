const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('Data & Storage keeps save import out of settings and Backup/Restore above Google Drive', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const pageStart = html.indexOf('id="dataStoragePage"');
  const pageEnd = html.indexOf('<!-- CREATE BACKUP -->');
  const page = html.slice(pageStart, pageEnd);
  assert.doesNotMatch(page, /Import \.sav/);
  assert.doesNotMatch(page, /dataStorageImportSaveBtn/);
  assert.ok(page.indexOf('Backup and restore') < page.indexOf('Google Drive'));
});

test('Google Drive recovery uses Google Identity + Drive API and never a folder picker', () => {
  const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
  const cloud = read('app/src/main/java/com/retra/emulator/CloudSyncCoordinator.kt');

  assert.match(main, /fun restoreFromGoogleDrive\(\)[\s\S]*cloudSync\.restoreFromDrive\(showResult = true\)/);
  assert.doesNotMatch(main, /cloudFolderPicker/);
  assert.doesNotMatch(settings, /requestCloudRecoveryFolder/);
  assert.match(cloud, /Identity\.getAuthorizationClient\(activity\)/);
  assert.match(cloud, /AuthorizationRequest\.builder\(\)/);
  assert.match(cloud, /https:\/\/www\.googleapis\.com\/auth\/drive\.file/);
  assert.doesNotMatch(cloud, /onFolderFallbackRequested|MODE_SAF|ACTION_OPEN_DOCUMENT_TREE/);
});

test('per-ROM gameplay menu import remains available', () => {
  const gameplay = read('app/src/main/java/com/retra/emulator/GameplayEnhancementController.kt');
  const flow = read('app/src/main/java/com/retra/emulator/SaveImportController.kt');
  assert.match(gameplay, /"Import save"/);
  assert.match(flow, /beginGameplaySaveImport/);
  assert.doesNotMatch(flow, /beginDataStorageSaveImport/);
});
