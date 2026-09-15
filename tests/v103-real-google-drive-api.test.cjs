const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const gradle = read('app/build.gradle.kts');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const cloud = read('app/src/main/java/com/retra/emulator/CloudSyncCoordinator.kt');
const drive = read('app/src/main/java/com/retra/emulator/GoogleDriveApiRepository.kt');
const backup = read('app/src/main/java/com/retra/emulator/BackupRepository.kt');
const html = read('app/src/main/assets/retra/index.html');
const js = read('app/src/main/assets/retra/settings-ui.js');

test('Google Drive uses Google Identity Services AuthorizationClient with drive.file', () => {
  assert.match(gradle, /com\.google\.android\.gms:play-services-auth:21\.6\.0/);
  assert.match(cloud, /Identity\.getAuthorizationClient\(activity\)/);
  assert.match(cloud, /AuthorizationRequest\.builder\(\)/);
  assert.match(cloud, /ClearTokenRequest\.builder\(\)/);
  assert.doesNotMatch(cloud, /ClearTokenRequest\.Builder\(\)/);
  assert.match(cloud, /setRequestedScopes\(driveScopes\)/);
  assert.match(cloud, /DRIVE_FILE_SCOPE = "https:\/\/www\.googleapis\.com\/auth\/drive\.file"/);
  assert.match(main, /ActivityResultContracts\.StartIntentSenderForResult\(\)/);
  assert.match(main, /cloudSync\.onAuthorizationResolutionResult/);
  assert.doesNotMatch(cloud, /getAuthToken\(|KEY_AUTHTOKEN|oauth2:/);
});

test('cloud backup and restore never use ACTION_OPEN_DOCUMENT_TREE or Use this folder', () => {
  assert.doesNotMatch(main, /cloudFolderPicker/);
  assert.doesNotMatch(settings, /requestCloudRecoveryFolder|requestCloudSyncFolder/);
  assert.doesNotMatch(cloud, /OpenDocumentTree|ACTION_OPEN_DOCUMENT_TREE|Use this folder|DocumentFile/);
  // SAF is intentionally still allowed for explicit local save-folder import.
  assert.match(main, /importSavesFolderPicker[\s\S]*OpenDocumentTree/);
});

test('Drive API stores backups under exactly My Drive/Retra Backups and reuses the folder', () => {
  assert.match(drive, /BACKUP_FOLDER_NAME = "Retra Backups"/);
  assert.match(drive, /"'root' in parents and trashed = false and mimeType = '\$FOLDER_MIME' and name = '\$BACKUP_FOLDER_NAME'"/);
  assert.match(drive, /findBackupFolder\(token\)\?\.let \{ return it \}/);
  assert.match(drive, /put\("parents", JSONArray\(\)\.put\("root"\)\)/);
  assert.match(drive, /put\("name", BACKUP_FOLDER_NAME\)/);
});

test('Backup Now uploads an immutable validated .retra snapshot and preserves older good backups', () => {
  assert.match(html, /id="cloudBackupNowBtn"/);
  assert.match(js, /AndroidBridge\.backupToGoogleDrive\(\)/);
  assert.match(main, /fun backupToGoogleDrive\(\)/);
  assert.match(cloud, /backupRepository\.create\(file, BackupRepository\.Selection\(\)\)/);
  assert.match(cloud, /backupRepository\.validate\(file\)/);
  assert.match(drive, /uploadType=multipart/);
  assert.match(drive, /open\(\"POST\", url, token\)/);
  assert.doesNotMatch(drive, /X-HTTP-Method-Override|method == "PATCH"|existingId/);
  assert.match(drive, /Protect every prior good backup/);
  assert.match(drive, /createdId\?\.let[\s\S]*deleteFile/);
});

test('fresh installation checks cloud before any local archive/upload', () => {
  const backupFn = cloud.slice(cloud.indexOf('private fun performBackup'), cloud.indexOf('private fun loadRestoreChoices'));
  const cloudCheck = backupFn.indexOf('driveApi.listBackups(token)');
  const localCheck = backupFn.indexOf('backupRepository.hasMeaningfulUserData()');
  const create = backupFn.indexOf('backupRepository.create(file');
  const upload = backupFn.indexOf('driveApi.uploadBackup');
  assert.ok(cloudCheck >= 0 && cloudCheck < localCheck && localCheck < create && create < upload);
  assert.match(backupFn, /A Retra backup already exists in Google Drive\. Restore it before creating a new backup/);
});

test('restore lists, downloads, checksum-validates, archive-validates, then restores', () => {
  assert.match(cloud, /driveApi\.listBackups\(token\)/);
  assert.match(cloud, /showRestoreChooser/);
  assert.match(cloud, /driveApi\.downloadBackup\(token, backup, target\)/);
  assert.match(drive, /Downloaded backup checksum is invalid/);
  const restore = cloud.slice(cloud.indexOf('private fun performRestore'), cloud.indexOf('private fun clearExpiredToken'));
  assert.ok(restore.indexOf('backupRepository.validate(target)') < restore.indexOf('backupRepository.restore(target)'));
  assert.match(backup, /fun restore\(file: File\): Result/);
});

test('Drive UI exposes account, last successful backup, progress and clear error status', () => {
  assert.match(settings, /cloudAccount/);
  assert.match(settings, /cloudLastBackupAt/);
  assert.match(settings, /cloudTransferLabel/);
  assert.match(settings, /cloudTransferProgress/);
  assert.match(js, /cloudAccount/);
  assert.match(js, /last successful backup/);
  assert.match(js, /cloudTransferActive/);
  assert.match(js, /cloudLastError/);
});

test('no OAuth client secret or server secret is hardcoded in source', () => {
  const all = [main, settings, cloud, drive, gradle].join('\n');
  assert.doesNotMatch(all, /client_secret|CLIENT_SECRET|AIza[0-9A-Za-z_-]{20,}|-----BEGIN PRIVATE KEY-----/);
});
