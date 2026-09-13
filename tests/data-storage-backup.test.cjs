const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = (path) => fs.readFileSync(path, 'utf8');
const html = read('app/src/main/assets/retra/index.html');
const js = read('app/src/main/assets/retra/settings-ui.js');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const backup = read('app/src/main/java/com/retra/emulator/BackupRepository.kt');
const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
const polish = read('app/src/main/assets/retra/ui-polish.css');

test('Data and Storage appears directly below Color Style in More', () => {
  const color = html.indexOf('<strong>Color Style</strong>');
  const data = html.indexOf('<strong>Data and Storage</strong>');
  const settings = html.indexOf('<strong>Settings</strong>');
  assert.ok(color >= 0 && data > color && settings > data);
});

test('Create backup page offers selective Retra data categories', () => {
  for (const key of ['saves','saveStates','cheats','library','layouts','artwork','settings']) {
    assert.match(html, new RegExp(`data-backup-key="${key}"`));
  }
  assert.match(html, /id="confirmCreateBackupBtn"[\s\S]*Create backup/);
  assert.match(js, /AndroidBridge\.createBackup\(JSON\.stringify\(selected\)\)/);
});

test('backup filename uses Retra_yyyyMMdd_HHmm.retra and Android save picker', () => {
  assert.match(backup, /"'Retra_'yyyyMMdd_HHmm'\.retra'"/);
  assert.match(activity, /ActivityResultContracts\.CreateDocument\("application\/octet-stream"\)/);
  assert.match(activity, /backupCreatePicker\.launch\(backupRepository\.suggestedFileName\(\)\)/);
});

test('portable backup excludes ROMs and BIOS and protects restore paths', () => {
  assert.match(backup, /ROM and BIOS files are not included/);
  assert.match(backup, /ALLOWED_TOP_LEVEL = setOf\([\s\S]*"Saves"[\s\S]*"Metadata"/);
  assert.doesNotMatch(backup, /persistentCategoryDir\("ROM/);
  assert.match(backup, /normalized\.split\('\/'\)\.any \{ it == "\.\." \}/);
  assert.match(backup, /MAX_UNCOMPRESSED_BYTES/);
});

test('restore applies portable settings and recreates library metadata placeholders', () => {
  assert.match(prefs, /fun restorePortableSettings\(values: Map<String, Any>\)/);
  assert.match(prefs, /isPortableSettingKey\(key\)/);
  assert.match(backup, /RomIdentityStore\.Record\(/);
  assert.match(backup, /fileAvailable = hasLocalFile/);
  assert.match(backup, /content_hash_\$romId/);
});

test('Data and Storage exposes create, restore, direct folder, and rounded storage summary actions', () => {
  assert.match(html, /id="dataStorageOpenFolderBtn"/);
  assert.match(html, /id="createBackupStartBtn"/);
  assert.match(html, /id="restoreBackupBtn"/);
  assert.match(js, /AndroidBridge\.restoreBackup\(\)/);
  assert.match(js, /Math\.round\(available\).*GB free/);
  assert.match(activity, /fun getStorageSummary\(\): String = storageSummaryJson\(\)/);
});


test('backup action rows vertically center icon and copy', () => {
  assert.match(polish, /\.backup-action-btn\s*\{[\s\S]*align-items:\s*center/);
  assert.match(polish, /\.backup-action-btn > svg,[\s\S]*\.backup-action-btn > div[\s\S]*align-self:\s*center/);
});

test('Create backup action bar is fixed with one accurate bottom clearance', () => {
  assert.match(polish, /\.backup-create-footer\s*\{[\s\S]*position:\s*fixed/);
  assert.doesNotMatch(polish, /\.backup-create-footer\s*\{[\s\S]{0,180}position:\s*sticky/);
  assert.match(polish, /body\.backup-create-open main\s*\{[\s\S]*padding-bottom:\s*0\s*!important[\s\S]*scroll-padding-bottom:\s*0\s*!important/);
  assert.match(polish, /body\.backup-create-open #createBackupPage\.active\s*\{[\s\S]*padding-bottom:\s*calc\(84px \+ var\(--device-safe-bottom\)\)\s*!important/);
  assert.match(read('app/src/main/assets/retra/library-ui.js'), /classList\.toggle\('backup-create-open', pageId === 'createBackupPage'\)/);
});
