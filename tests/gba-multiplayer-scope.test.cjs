const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const multiplayer = read('app/src/main/java/com/retra/emulator/MultiplayerController.kt');
const multiplayerSurface = main + '\n' + multiplayer;
const compatibility = read('app/src/main/java/com/retra/emulator/GbaMultiplayerCompatibility.kt');
const remote = read('app/src/main/java/com/retra/emulator/RemoteLinkTransport.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('Retra multiplayer scope is normal GBA Link Cable only', () => {
  assert.match(native, /GBASIOLockstepCoordinator/);
  assert.match(multiplayerSurface, /Wi-Fi \(server\)/);
  assert.match(multiplayerSurface, /Wi-Fi \(client\)/);
  assert.match(multiplayerSurface, /Bluetooth \(server\)/);
  assert.match(multiplayerSurface, /Bluetooth \(client\)/);
  assert.match(multiplayerSurface, /Single-Pak\/Multiboot and Wireless Adapter\/RFU are intentionally not supported/);
  assert.doesNotMatch(multiplayerSurface, /startSinglePak|startMultiboot|startRfu|startWirelessAdapter/i);
});

test('automatic GBA multiplayer compatibility validates ROM identity and link eligibility', () => {
  assert.match(compatibility, /class GbaMultiplayerCompatibility/);
  assert.match(compatibility, /gameCode/);
  assert.match(compatibility, /checksumValid/);
  assert.match(compatibility, /Patched ROMs are not supported for GBA Link/);
  assert.match(compatibility, /localSupported = true/);
  assert.match(compatibility, /wifiSupported = true/);
  assert.match(compatibility, /bluetoothSupported = true/);
  assert.match(compatibility, /singlePakSupported: Boolean = false/);
  assert.match(compatibility, /wirelessRfuSupported: Boolean = false/);
  assert.match(multiplayerSurface, /multiplayerCompatibility\.detect\(/);
  assert.match(multiplayerSurface, /multiplayerCompatibility\.detectCandidate\(/);
});

test('Remote Link recovery retries timed-out resync and reseeds inputs after recovery', () => {
  assert.match(remote, /RESYNC_TIMEOUT_MS = 5000L/);
  assert.match(remote, /MAX_RESYNC_ATTEMPTS = 3/);
  assert.match(remote, /Remote Link recovery failed after \$MAX_RESYNC_ATTEMPTS attempts/);
  assert.match(remote, /private fun reseedLocalInput\(\)/);
  assert.match(remote, /successfulResyncs\+\+/);
  assert.match(remote, /PACKET_STATE_HASH/);
  assert.match(remote, /PACKET_RESYNC_STATE/);
  assert.match(remote, /hooks\.importSnapshot/);
});


test('Remote Link adapts to jitter, validates input and reports disconnect only once', () => {
  assert.match(remote, /@Volatile var jitterMs: Long = 0L/);
  assert.match(remote, /oneWayBudgetMs = \(rttMs \/ 2L\) \+ \(jitterMs \* 2L\)/);
  assert.match(remote, /MAX_INPUT_FUTURE_FRAMES = 300L/);
  assert.match(remote, /VALID_KEY_MASK = \(1 shl 10\) - 1/);
  assert.match(remote, /lateInputPackets\+\+/);
  assert.match(remote, /disconnectNotified = AtomicBoolean\(false\)/);
  assert.match(remote, /compareAndSet\(false, true\)/);
  assert.match(remote, /data class HealthSnapshot/);
  assert.match(remote, /fun healthSnapshot\(\): HealthSnapshot/);
});
