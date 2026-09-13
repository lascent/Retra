const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.join(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const persistence = read('app/src/main/java/com/retra/emulator/RomPersistenceController.kt');
const transfer = read('app/src/main/java/com/retra/emulator/SaveTransferRepository.kt');
const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
const provider = read('app/src/main/java/com/retra/emulator/RetraDocumentsProvider.kt');
const manifest = read('app/src/main/AndroidManifest.xml');
const html = read('app/src/main/assets/retra/index.html');

test('Retra registers a system DocumentsProvider like an app storage location', () => {
  assert.match(manifest, /android:name="\.RetraDocumentsProvider"/);
  assert.match(manifest, /android:authorities="com\.retra\.emulator\.documents"/);
  assert.match(manifest, /android:permission="android\.permission\.MANAGE_DOCUMENTS"/);
  assert.match(manifest, /android\.content\.action\.DOCUMENTS_PROVIDER/);
  assert.match(provider, /class RetraDocumentsProvider : DocumentsProvider\(\)/);
  assert.match(provider, /Root\.COLUMN_TITLE -> row\.add\("Retra"\)/);
  assert.match(provider, /Root\.COLUMN_AVAILABLE_BYTES/);
});

test('DocumentsProvider projection conversion is Kotlin-compile-safe', () => {
  assert.doesNotMatch(provider, /projection\?\.toTypedArray\(\)/);
  assert.match(provider, /private fun copyProjection\(projection: Array<out String>\?, fallback: Array<String>\): Array<String>/);
  assert.match(provider, /Array\(source\.size\) \{ index -> source\[index\] \}/);
  assert.match(provider, /copyProjection\(projection, DEFAULT_ROOT_PROJECTION\)/);
  assert.match(provider, /copyProjection\(projection, DEFAULT_DOCUMENT_PROJECTION\)/);
});

test('provider exposes the exact managed persistent_data directory', () => {
  assert.match(provider, /File\(ctx\.filesDir, "persistent_data"\)/);
  assert.match(provider, /"Saves", "SaveStates", "Cheats", "Config", "Layouts"/);
  assert.match(provider, /override fun queryChildDocuments/);
  assert.match(provider, /override fun openDocument/);
  assert.match(provider, /Document\.FLAG_SUPPORTS_WRITE/);
  assert.match(provider, /override fun createDocument/);
  assert.match(provider, /override fun deleteDocument/);
  assert.match(provider, /override fun renameDocument/);
});

test('Open app folder directly views Retra with no Use this folder flow', () => {
  const openStart = settings.indexOf('internal fun MainActivity.openAppFolderInternal()');
  const openEnd = settings.indexOf('internal fun MainActivity.deleteCloudPathsAsync', openStart);
  const openBody = settings.slice(openStart, openEnd);
  assert.match(openBody, /Intent\(Intent\.ACTION_VIEW\)/);
  assert.match(openBody, /RetraDocumentsProvider\.rootUri\(\)/);
  assert.match(openBody, /DocumentsContract\.Root\.MIME_TYPE_ITEM/);
  assert.doesNotMatch(openBody.replace(/\/\/[^\n]*$/gm, ''), /ACTION_OPEN_DOCUMENT_TREE|appFolderPicker|\.launch\(/);
  assert.doesNotMatch(main, /internal val appFolderPicker/);
});

test('saves stay in Retra-owned storage and provider refresh keeps settings visible', () => {
  const syncStart = settings.indexOf('internal fun MainActivity.syncAppFolderAsync');
  const syncEnd = settings.indexOf('internal fun MainActivity.exportSaveDataToTreeAsync', syncStart);
  const syncBody = settings.slice(syncStart, syncEnd);
  assert.match(syncBody, /appFolderSyncDirty\.set\(true\)/);
  assert.match(syncBody, /appFolderSyncQueued\.compareAndSet\(false, true\)/);
  assert.match(syncBody, /writePortableMetadataFiles\(\)/);
  assert.match(syncBody, /RetraDocumentsProvider\.notifyDataChanged\(this\)/);
  assert.doesNotMatch(syncBody, /saveTransfer\.export\(/);
  assert.match(persistence, /commitWorkingSave[\s\S]*syncAppFolderAsync\(showResult = false\)/);
  assert.match(main, /syncAppFolderAsync\(showResult = false\)[\s\S]*super\.onPause\(\)/);
});

test('portable metadata includes settings inside the built-in Retra folder', () => {
  assert.match(persistence, /File\(metadataDir, "settings\.json"\)/);
  assert.match(persistence, /prefs\.portableSettingsSnapshot\(\)/);
  assert.match(prefs, /fun portableSettingsSnapshot\(\)/);
  assert.match(html, /Open Retra storage in Android Files/);
});

test('explicit SAF export/import still reuses provider directories without duplicates', () => {
  assert.match(transfer, /private fun ensureDirectory\(parent: DocumentFile, name: String\)/);
  assert.match(transfer, /parent\.listFiles\(\)\.toList\(\)/);
  assert.match(transfer, /child\.name == name/);
  assert.match(transfer, /RegexOption\.IGNORE_CASE/);
  assert.match(transfer, /minByOrNull \{ it\.first \}/);
  assert.match(transfer, /dir = ensureDirectory\(dir, segment\)/);
});

test('explicit export updates an existing SAF file rather than creating duplicates', () => {
  assert.match(transfer, /return findExistingFile\(dir, name\)\s*\?: dir\.createFile/);
  assert.match(transfer, /openOutputStream\(document\.uri, "wt"\)/);
  assert.match(transfer, /copyFileToDocumentVerified\(file, document\)/);
});
