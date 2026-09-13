const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const read = p => fs.readFileSync(p, 'utf8');
const backup = read('app/src/main/java/com/retra/emulator/BackupRepository.kt');
const persistence = read('app/src/main/java/com/retra/emulator/RomPersistenceController.kt');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const ui = read('app/src/main/assets/retra/library-ui.js');
const shell = read('app/src/main/assets/retra/app-shell.js');
const settings = read('app/src/main/assets/retra/settings-ui.js');

test('restore recreates a missing-ROM placeholder under the original romId', () => {
  assert.match(backup, /val existing = romIdentityStore\.getById\(romId\)/);
  assert.match(backup, /fileAvailable = hasLocalFile/);
  assert.match(backup, /launchPath = existingLaunch\?\.absolutePath/);
  assert.match(backup, /patchPath = existingPatch\?\.absolutePath/);
  assert.match(backup, /putString\("content_hash_\$romId", contentHash\)/);
  assert.match(backup, /remove\("content_path_\$romId"\)/);
});

test('portable library metadata carries reconnect identity, playtime and history statistics', () => {
  for (const field of ['contentHash','hashAlgorithm','playtimeMs','lastPlayedAt','playCount','completed','finalContentHash','legacyIdentityHash','createdAt']) {
    assert.match(persistence, new RegExp(`put\\("${field}"`));
  }
  assert.match(backup, /restoredPlaytime = maxOf/);
  assert.match(backup, /last_played_at_v1_\$romId/);
  assert.match(backup, /play_count_v1_\$romId/);
});

test('native library exports history statistics and accepts portable history sync', () => {
  assert.match(activity, /put\("lastPlayedAt"/);
  assert.match(activity, /put\("playCount"/);
  assert.match(activity, /fun syncRomPlayHistory\(historyJson: String\): Int/);
  assert.match(activity, /fun syncRomCompletionState\(stateJson: String\): Int/);
  assert.match(shell, /AndroidBridge\.syncRomCompletionState/);
  assert.match(ui, /AndroidBridge\.syncRomPlayHistory\(JSON\.stringify\(playHistory\.slice\(0, 100\)\)\)/);
});

test('restored missing ROMs remain visible and matching imports mark them playable', () => {
  assert.match(ui, /article\.dataset\.fileAvailable = fileAvailable \? 'true' : 'false'/);
  assert.match(ui, /'ROM file required'/);
  assert.match(shell, /label\.textContent = 'Add ROM to Play'/);
  assert.match(shell, /fileAvailable: true/);
});

test('restored native history is merged back into History and statistics immediately', () => {
  assert.match(shell, /const nativeHistory = \[\]/);
  assert.match(shell, /playHistory = \[\.\.\.historyById\.values\(\)\]/);
  assert.match(settings, /savePlayHistory/);
  assert.match(settings, /renderHistory/);
  assert.match(settings, /updateStatistics/);
});
