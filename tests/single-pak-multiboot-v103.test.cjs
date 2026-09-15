const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const multiplayer = read('app/src/main/java/com/retra/emulator/MultiplayerController.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('v1.0.3 Local Link exposes a real Single-Pak/Multiboot path', () => {
  assert.match(multiplayer, /Single-Pak \/ Multiboot/);
  assert.match(multiplayer, /Requires a selected 16 KiB GBA BIOS/);
  assert.match(multiplayer, /startLocalSinglePakSession/);
  assert.match(main, /external fun startLocalSinglePak\(/);
  assert.match(native, /Java_com_retra_emulator_MainActivity_startLocalSinglePak/);
});

test('Single-Pak native receiver is BIOS-only and shares mGBA lockstep SIO', () => {
  assert.match(native, /prepareLocalSinglePakClient/);
  assert.match(native, /mCoreCreate\(mPLATFORM_GBA\)/);
  assert.match(native, /loadBIOS\(player\.core, bios, 0\)/);
  assert.match(native, /mPERIPH_GBA_LINK_PORT/);
  assert.match(native, /GBASIOLockstepCoordinatorAttach/);
  assert.match(native, /SINGLE_PAK_BOOT_KEYS/);
  assert.match(native, /SINGLE_PAK_BOOT_HOLD_FRAMES/);
});

test('Single-Pak keeps only the cartridge save and does not invent a client save', () => {
  assert.match(multiplayer, /activeLinkFirstRomId = firstId/);
  assert.match(multiplayer, /activeLinkSecondRomId = null/);
  assert.match(multiplayer, /localLinkPlayer2Title = "Single-Pak Client"/);
});
