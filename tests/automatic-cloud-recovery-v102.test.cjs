const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = path => fs.readFileSync(path, 'utf8');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const coordinator = read('app/src/main/java/com/retra/emulator/CloudSyncCoordinator.kt');
const drive = read('app/src/main/java/com/retra/emulator/GoogleDriveApiRepository.kt');
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
  assert.match(activity, /restoreFromGoogleDrive[\s\S]*cloudSync\.restoreFromDrive\(showResult = true\)/);
});

test('explicit reinstall recovery lists Drive snapshots before downloading and never creates a folder', () => {
  assert.match(drive, /fun listBackups\(accessToken: String\)[\s\S]*findBackupFolder\(accessToken\) \?: return emptyList\(\)/);
  assert.match(coordinator, /loadRestoreChoices[\s\S]*driveApi\.listBackups\(token\)/);
  assert.match(coordinator, /driveApi\.downloadBackup\(token, backup, target\)/);
  assert.match(backup, /backupRepository|fun restore\(file: File\)/);
});

test('downloaded cloud archive is validated and restored through portable backup metadata', () => {
  assert.match(backup, /fun validate\(file: File\): Result/);
  assert.match(backup, /fun restore\(file: File\): Result/);
  assert.match(backup, /restoreLibraryMetadata\(File\(stagedMetadata, "library\.json"\)\)/);
  assert.match(backup, /restorePortableSettings\(settingsFile\)/);
  assert.match(coordinator, /backupRepository\.validate\(target\)/);
  assert.match(coordinator, /backupRepository\.restore\(target\)/);
  assert.match(activity, /window\.retraBackupRestored/);
});

test('automatic cloud protection is debounced and never drops a change made during backup', () => {
  assert.match(coordinator, /AUTO_BACKUP_DEBOUNCE_MS = 1_500L/);
  assert.match(coordinator, /pendingAutoBackup = true/);
  assert.match(coordinator, /if \(pendingAutoBackup && isAutomaticBackupEnabled\(\)\)/);
  assert.match(coordinator, /requestAutoSync\(urgent = true\)/);
  assert.match(settings, /if \(showResult\) cloudSync\.backupNow\(showResult = true\)/);
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

test('Drive UI reports account, last backup, transfer progress and errors', () => {
  assert.match(settings, /cloudLastBackupAt/);
  assert.match(settings, /cloudTransferProgress/);
  assert.match(coordinator, /LAST_SUCCESS_PREF/);
  assert.match(js, /last successful backup/);
  assert.match(js, /cloudTransferProgress/);
  assert.match(js, /backup needs attention/);
});

test('Android Auto Backup is narrowed to portable Retra state', () => {
  for (const rules of [backupRules, extractionRules]) {
    assert.match(rules, /include domain="file" path="persistent_data\/"/);
    assert.match(rules, /include domain="file" path="datastore\/"/);
    assert.match(rules, /include domain="database" path="\."/);
    assert.doesNotMatch(rules, /<exclude domain="file" path="library_content\/" \/>/);
    assert.doesNotMatch(rules, /<exclude domain="file" path="save_work\/" \/>/);
  }
});
