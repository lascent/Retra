const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('landscape primary controls default to 105% while utility controls stay independent', () => {
  const native = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
  const editor = read('app/src/main/assets/retra/controller-editor-ui.js');
  assert.match(native, /landscapePrimaryScale = 1\.05f/);
  assert.match(native, /buttonL, binding\.buttonR, binding\.dpadContainer, binding\.abContainer/);
  assert.match(editor, /DEFAULT_CONTROLLER_SCALE_LANDSCAPE_PRIMARY = 1\.05/);
  assert.match(editor, /landscapePrimaryBaseControls = new Set\(\['shoulderLeft', 'shoulderRight', 'dpad', 'ab'\]\)/);
});

test('Screen Editor has a persistent editor-only grid and aligned safe toolbar row', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const js = read('app/src/main/assets/retra/screen-editor.js');
  const css = read('app/src/main/assets/retra/style-editor-modern.css');
  assert.match(html, /id="screenGridToggleBtn"/);
  assert.match(js, /retraScreenEditorGridV1/);
  assert.match(js, /screen-grid-enabled/);
  assert.match(css, /\.emulator-preview\.screen-grid-enabled::before/);
  assert.match(css, /body\.screen-editor-landscape \.screen-size-page > \.sub-header,[\s\S]*?top:66px !important;[\s\S]*?height:44px !important;/);
  assert.match(css, /\.sub-header \.back-btn,[\s\S]*?\.preview-add-fab\{[\s\S]*?height:44px !important;/);
});

test('gameplay menu exposes bounded Rewind, Edit layout, and strict per-ROM save import', () => {
  const gameplay = read('app/src/main/java/com/retra/emulator/GameplayEnhancementController.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  const transfer = read('app/src/main/java/com/retra/emulator/SaveTransferRepository.kt');
  assert.match(gameplay, /"Rewind"/);
  assert.match(gameplay, /listOf\(5, 10, 15\)/);
  assert.match(gameplay, /"Edit layout"/);
  assert.match(gameplay, /"Import save"/);
  assert.match(native, /RETRA_REWIND_MAX_BYTES = 32u \* 1024u \* 1024u/);
  assert.match(native, /RETRA_REWIND_MAX_AGE_MS = 16'500/);
  assert.match(native, /lastRewindFrameWallClock/);
  assert.match(native, /Java_com_retra_emulator_MainActivity_rewindSeconds/);
  assert.match(transfer, /importExt !in setOf\("sav", "srm"\)/);
  assert.match(transfer, /This \.\$importExt does not match/);
  assert.match(transfer, /existing\.length\(\) != importedSize/);
});

test('single .sav/.srm import is gameplay-menu only and safely reloads the current ROM', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const settings = read('app/src/main/assets/retra/settings-ui.js');
  const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const gameplay = read('app/src/main/java/com/retra/emulator/GameplayEnhancementController.kt');
  const flow = read('app/src/main/java/com/retra/emulator/SaveImportController.kt');
  const saveData = read('app/src/main/java/com/retra/emulator/SaveDataRepository.kt');
  assert.doesNotMatch(html, /id="dataStorageImportSaveBtn"/);
  assert.doesNotMatch(html, /<strong>Import \.sav<\/strong>/);
  assert.doesNotMatch(settings, /AndroidBridge\.importSaveFile\(\)/);
  assert.doesNotMatch(main, /fun importSaveFile\(\)/);
  assert.match(gameplay, /"Import save"/);
  assert.match(flow, /beginGameplaySaveImport/);
  assert.match(flow, /shutdownCore\(\)/);
  assert.match(flow, /commitActiveWorkingSaves\(\)/);
  assert.match(flow, /saveStates\.autoFile\(session\.romId\)\.delete\(\)/);
  assert.match(flow, /loadRomFile\(rom, session\.title, patch, session\.romId\)/);
  assert.match(saveData, /backupBatterySaveIfChanged\(romId\)/);
  assert.match(saveData, /atomicCopyVerified\(source, canonical\)/);
});

test('automatic Google Drive backup is in Data & Storage, not Misc', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const dataStart = html.indexOf('<!-- DATA AND STORAGE -->');
  const miscStart = html.indexOf('<!-- MISC -->');
  const cloud = html.indexOf('Automatic Google Drive backup');
  assert.ok(dataStart >= 0 && cloud > dataStart && cloud < miscStart);
  const misc = html.slice(miscStart, html.indexOf('<!-- ADVANCED -->'));
  assert.doesNotMatch(misc, /Automatic Google Drive backup/);
});

test('Backup and restore is shown above the Google Drive section', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const backup = html.indexOf('>Backup and restore</div>');
  const googleDrive = html.indexOf('>Google Drive</div>');
  assert.ok(backup >= 0 && googleDrive >= 0 && backup < googleDrive);
});

test('Restore from Google Drive uses the real Drive API and never the document-tree picker', () => {
  const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
  assert.match(main, /fun restoreFromGoogleDrive\(\)[\s\S]*cloudSync\.restoreFromDrive\(showResult = true\)/);
  assert.doesNotMatch(main, /cloudFolderPicker/);
  assert.doesNotMatch(settings, /requestCloudRecoveryFolder|requestCloudSyncFolder/);
  assert.match(main, /StartIntentSenderForResult/);
});

test('explicit Google Drive restore is remote-first and backup upload is immutable', () => {
  const drive = read('app/src/main/java/com/retra/emulator/GoogleDriveApiRepository.kt');
  const cloud = read('app/src/main/java/com/retra/emulator/CloudSyncCoordinator.kt');
  const backup = read('app/src/main/java/com/retra/emulator/BackupRepository.kt');
  assert.match(drive, /findBackupFolder\(accessToken\) \?: return emptyList\(\)/);
  assert.match(drive, /BACKUP_FOLDER_NAME = "Retra Backups"/);
  assert.match(drive, /parents", JSONArray\(\)\.put\("root"\)/);
  assert.match(drive, /requestMultipart\(endpoint/);
  assert.doesNotMatch(drive, /X-HTTP-Method-Override|existingId/);
  assert.match(cloud, /val cloudBackups = driveApi\.listBackups\(token\)[\s\S]*hasMeaningfulUserData/);
  assert.match(backup, /fun validate\(file: File\): Result/);
  assert.match(cloud, /backupRepository\.validate\(target\)[\s\S]*backupRepository\.restore\(target\)/);
});

test('gameplay layout editor returns directly to the running game and commits its profile', () => {
  const appShell = read('app/src/main/assets/retra/app-shell.js');
  const profiles = read('app/src/main/assets/retra/layout-profiles.js');
  assert.match(appShell, /window\.retraOpenInGameLayoutEditor/);
  assert.match(appShell, /classList\.add\('in-game-settings', 'in-game-layout-editor'\)/);
  assert.match(profiles, /in-game-layout-editor/);
  assert.match(profiles, /commitScreenEditorState\(\)/);
  assert.match(profiles, /AndroidBridge\.closeInGameSettings\(\)/);
});
