const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('speed contract is based on GBA emulated time, not ROM identity', () => {
  const contract = read('app/src/main/java/com/retra/emulator/FastForwardSpeedContract.kt');
  assert.match(contract, /fun targetEmulatedFps\(/);
  assert.match(contract, /baseFps\(frameTimeNs\) \* speed/);
  assert.match(contract, /fun targetElapsedNs\(/);
  assert.doesNotMatch(contract, /FireRed|SoulGold|Pokemon|romTitle|romId/i);
});

test('presentation cadence cannot change the requested multiplier', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
  assert.match(session, /sliceCadenceNs = TurboFramePolicy\.sliceCadenceNs/);
  assert.match(session, /turboGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
  assert.match(governor, /FastForwardSpeedContract\.targetElapsedNs/);
});

test('stable turbo presentation is capped at 120 Hz instead of chasing extreme panel modes', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const display = read('app/src/main/java/com/retra/emulator/DisplayPerformanceManager.kt');
  assert.match(session, /coerceAtMost\(120f\)/);
  assert.match(display, /maxTurboRefreshRateHz: Float = 120f/);
  assert.doesNotMatch(session, /144|165|extremePresentationHz/);
});

test('renderer quality is not silently reduced by Speed Mode', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(session, /userFrameSkip/);
  assert.doesNotMatch(session, /adaptiveCoreFrameSkip|speedProtectingCoreFrameSkip|constrainedCoreFrameSkip/);
});
