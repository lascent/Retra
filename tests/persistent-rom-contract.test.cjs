const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const main = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const romUi = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/RomUiController.kt'), 'utf8');
const store = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/RomIdentityStore.kt'), 'utf8');
const fileOps = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/RetraFileOps.kt'), 'utf8');
const saveData = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/SaveDataRepository.kt'), 'utf8');
const ui = readWebJs(path.join(__dirname, '..'));

test('persistent identity remains romId primary + SHA-256 reconnect matcher', () => {
  assert.match(store, /@PrimaryKey\s+@ColumnInfo\(name = "rom_id"\)\s+val romId/);
  assert.match(store, /@ColumnInfo\(name = "content_hash"/);
  assert.match(fileOps, /MessageDigest\.getInstance\("SHA-256"\)/);
  assert.match(romUi, /romIdentityStore\.findByHash\(system, contentHash\)/);
});

test('remove from Library archives identity instead of deleting game data', () => {
  const start = ui.indexOf('async function removeRomFromLibrary');
  const end = ui.indexOf('async function deleteRomGameData', start);
  const removeScope = ui.slice(start, end);
  assert.match(removeScope, /archived: true/);
  assert.match(removeScope, /AndroidBridge\.archiveRom/);
  assert.doesNotMatch(removeScope, /deleteGameData/);
  assert.doesNotMatch(removeScope, /deleteStoredRomFile/);
});

test('save commits retain verified atomic-write and backup protections', () => {
  assert.match(saveData, /atomicCopyVerified/);
  assert.match(saveData, /saveLockFor\(romId\)/);
  assert.match(saveData, /backupDirectory\(romId\)/);
  assert.match(saveData, /legacyMigrationVerified/);
});
