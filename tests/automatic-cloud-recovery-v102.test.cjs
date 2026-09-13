const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = path => fs.readFileSync(path, 'utf8');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const coordinator = read('app/src/main/java/com/retra/emulator/CloudSyncCoordinator.kt');
const drive = read('app/src/main/java/com/retra/emulator/GoogleDriveApiRepository.kt');
const transfer = read('app/src/main/java/com/retra/emulator/SaveTransferRepository.kt');
const backup = read('app/src/main/java/com/retra/emulator/BackupRepository.kt');
const html = read('app/src/main/assets/retra/index.html');
const js = read('app/src/main/assets/retra/settings-ui.js');
const backupRules = read('app/src/main/res/xml/backup_rules.xml');
const extractionRules = read('app/src/main/res/xml/data_extraction_rules.xml');

test('Data and Storage exposes an explicit Google Drive reinstall recovery action', () => {
  assert.match(html, /id="restoreCloudBackupBtn"/);
  assert.match(html, /Restore from Google Drive/);
  assert.match(js, /AndroidBridge\.restoreFromGoogleDrive\(\)/);
  assert.match(activity, /fun restoreFromGoogleDrive\(\)/);
  assert.match(settings, /fun MainActivity\.requestCloudRecoveryAccount\(\)/);
});

test('explicit reinstall recovery is remote-first on an unpaired device', () => {
  assert.match(coordinator, /restoreRemoteFirst: Boolean = false/);
  assert.match(drive, /fun sync\(accessToken: String, preferRemoteOnFirstSync: Boolean = false\)/);
  assert.match(transfer, /fun syncDetailed\(rootUri: Uri, preferRemoteOnFirstSync: Boolean = false\)/);
  assert.match(drive, /previous == null && preferRemoteForUnpairedEmptyInstall/);
  assert.match(transfer, /previous == null && preferRemoteForUnpairedEmptyInstall/);
});

test('downloaded cloud metadata is reapplied to Room and portable settings', () => {
  assert.match(backup, /fun applyPortableMetadataFromPersistentData\(\): Int/);
  assert.match(backup, /restoreLibraryMetadata\(library\)/);
  assert.match(backup, /restorePortableSettings\(settings\)/);
  assert.match(activity, /backupRepository\.applyPortableMetadataFromPersistentData\(\)/);
  assert.match(activity, /window\.retraBackupRestored/);
});

test('automatic cloud protection is debounced and never drops a change made during sync', () => {
  assert.match(coordinator, /AUTO_SYNC_DEBOUNCE_MS = 1_500L/);
  assert.match(coordinator, /pendingAutoSync = true/);
  assert.match(coordinator, /if \(pendingAutoSync\)/);
  assert.match(coordinator, /requestAutoSync\(urgent = true\)/);
  assert.match(settings, /if \(showResult\) cloudSync\.sync\(showResult = true\)/);
  assert.match(settings, /else cloudSync\.requestAutoSync\(urgent = false\)/);
});

test('settings/statistics and lifecycle exit request automatic protection', () => {
  assert.match(settings, /if \(commit\) syncAppFolderAsync\(showResult = false\)/);
  assert.match(settings, /if \(commit\) cloudSync\.requestAutoSync\(urgent = false\)/);
  assert.match(activity, /updateRomLibraryMetadata[\s\S]*syncCloudAsync\(showResult = false\)/);
  assert.match(activity, /syncRomCompletionState[\s\S]*syncCloudAsync\(showResult = false\)/);
  assert.match(activity, /syncRomPlayHistory[\s\S]*syncCloudAsync\(showResult = false\)/);
  assert.match(activity, /flushCloudBackupAsync\(\)/);
});

test('Drive UI reports last protection state rather than only connection state', () => {
  assert.match(settings, /cloudLastBackupAt/);
  assert.match(coordinator, /LAST_SUCCESS_PREF/);
  assert.match(js, /Protected • last backup/);
  assert.match(js, /backup needs attention/);
});

test('Android Auto Backup is narrowed to portable Retra state', () => {
  for (const rules of [backupRules, extractionRules]) {
    assert.match(rules, /include domain="file" path="persistent_data\/"/);
    assert.match(rules, /include domain="file" path="datastore\/"/);
    assert.match(rules, /include domain="database" path="\."/);
    // Explicit includes are an allowlist; unlisted ROM/working paths remain excluded.
    assert.doesNotMatch(rules, /<exclude domain="file" path="library_content\/" \/>/);
    assert.doesNotMatch(rules, /<exclude domain="file" path="save_work\/" \/>/);
  }
});
