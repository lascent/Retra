const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs } = require('./helpers/assets.cjs');

const root = path.join(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const romUi = read('app/src/main/java/com/retra/emulator/RomUiController.kt');
const persistence = read('app/src/main/java/com/retra/emulator/RomPersistenceController.kt');
const store = read('app/src/main/java/com/retra/emulator/RomIdentityStore.kt');
const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
const journal = read('app/src/main/java/com/retra/emulator/ImportJournal.kt');
const layouts = read('app/src/main/java/com/retra/emulator/GameplayLayoutRepository.kt');
const assets = read('app/src/main/java/com/retra/emulator/RomAssetRepository.kt');
const saveData = read('app/src/main/java/com/retra/emulator/SaveDataRepository.kt');
const saveStates = read('app/src/main/java/com/retra/emulator/SaveStateRepository.kt');
const cheats = read('app/src/main/java/com/retra/emulator/CheatRepository.kt');
const gradle = read('app/build.gradle.kts');
const native = read('app/src/main/cpp/native-lib.cpp');
const ui = readWebJs(root);

test('Room replaces SQLiteOpenHelper without changing the existing database filename', () => {
  assert.match(store, /@Database\(entities = \[RomRecordEntity::class, RomIdentityAliasEntity::class\], version = 2/);
  assert.match(store, /DATABASE_NAME = "retra_rom_identity\.db"/);
  assert.match(store, /Migration\(1, 2\)/);
  assert.doesNotMatch(store, /import android\.database\.sqlite\.SQLiteOpenHelper/);
  assert.doesNotMatch(store, /:\s*SQLiteOpenHelper/);
  assert.match(gradle, /androidx\.room\.runtime/);
  assert.match(gradle, /ksp\(libs\.androidx\.room\.compiler\)/);
});

test('Room schema owns library metadata and fingerprint cache', () => {
  for (const column of ['rom_id','content_hash','platform','display_name','current_file_uri','favorite','categories_json','playtime_ms','archived','file_size','last_modified']) {
    assert.match(store, new RegExp(`name = "${column}"`));
  }
  assert.match(store, /findByFingerprint/);
});

test('DataStore is authoritative for app-wide preferences', () => {
  assert.match(prefs, /PreferenceDataStoreFactory\.create/);
  assert.match(prefs, /DATASTORE_NAME = "retra_global_preferences"/);
  assert.match(prefs, /key\.startsWith\("ui_"\)/);
  assert.match(ui, /setNativeUiPreference\('font'/);
  assert.match(ui, /setNativeUiPreference\('appearance'/);
  assert.match(prefs, /"fast_forward_speed_v1"/);
  assert.match(main, /FAST_FORWARD_SPEED_PREF/);
  assert.match(settings, /put\("fastForwardSpeed"/);
});

test('imports use a durable four-state crash journal and recover on launch', () => {
  for (const state of ['PREPARING','ROM_READY','DATABASE_COMMITTED','COMPLETE']) assert.match(journal, new RegExp(state));
  assert.match(main, /recoverInterruptedImports\(\)/);
  assert.match(romUi, /ImportJournal\.State\.ROM_READY/);
  assert.match(romUi, /ImportJournal\.State\.DATABASE_COMMITTED/);
});

test('per-ROM files include layouts, media, saves, states, cheats and config roots', () => {
  assert.match(layouts, /fun layoutFile\(romId: String, portrait: Boolean\)/);
  assert.match(layouts, /"portrait\.json" else "landscape\.json"/);
  assert.match(saveData, /persistentCategoryDir\("Saves"\)/);
  assert.match(saveStates, /persistentCategoryDir\("SaveStates"\)/);
  assert.match(cheats, /persistentCategoryDir\("Cheats"\)/);
  assert.match(assets, /persistentCategoryDir\("Config"\)/);
  assert.match(layouts, /persistentCategoryDir\("Layouts"\)/);
  assert.match(assets, /persistentCategoryDir\("Covers"\)/);
  assert.match(assets, /persistentCategoryDir\("Backgrounds"\)/);
  assert.match(main, /getRomGameplayLayout/);
  assert.match(main, /commitRomGameplayLayout/);
  assert.match(cheats, /fun cheatFile\(romId: String\)/);
  assert.match(cheats, /atomicWriteText\(cheatFile\(romId\), json\)/);
  assert.match(assets, /fun getConfig\(romId: String\)/);
  assert.match(assets, /fun setConfig\(romId: String, json: String\)/);
});

test('custom covers/backgrounds are filesystem-backed by romId', () => {
  assert.match(assets, /"cover" -> File\(File\(fileOps\.persistentCategoryDir\("Covers"\), safeId/);
  assert.match(assets, /"background" -> File\(File\(fileOps\.persistentCategoryDir\("Backgrounds"\), safeId/);
  assert.match(ui, /saveNativeRomMedia\(rom\.id, 'cover'/);
  assert.match(ui, /saveNativeRomMedia\(romId, 'background'/);
});

test('patched identity hashes the final materialized playable ROM and keeps aliases', () => {
  assert.match(persistence, /materializedPatchedHash/);
  assert.match(native, /materializePatchedRom/);
  assert.match(store, /rom_identity_aliases/);
  assert.match(persistence, /legacyBundleIdentityHash/);
  assert.match(persistence, /reconcilePatchedIdentity/);
});

test('library remove is non-destructive while explicit data deletion remains separate', () => {
  assert.match(main, /fun archiveRom\(romId: String\): Boolean = archiveRomRecord\(romId\)/);
  assert.match(main, /fun deleteGameData\(romId: String\): Boolean = deleteGameDataInternal\(romId\)/);
});
