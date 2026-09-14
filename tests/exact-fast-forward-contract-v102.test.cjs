const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('fast-forward uses an immediate speed-protecting renderer budget', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
  assert.match(policy, /fun speedProtectingCoreFrameSkip\(/);
  assert.match(policy, /FastForwardSpeedContract\.rendererFrameSkip/);
  assert.match(session, /speedProtectingCoreFrameSkip\([\s\S]*FRAME_TIME_NS[\s\S]*targetPresentationHz/);
  assert.match(governor, /FastForwardSpeedContract\.targetElapsedNs/);
});

test('speed contract is based on GBA emulated time, not ROM identity or loop count', () => {
  const contract = read('app/src/main/java/com/retra/emulator/FastForwardSpeedContract.kt');
  assert.match(contract, /fun targetEmulatedFps\(/);
  assert.match(contract, /baseFps\(frameTimeNs\) \* speed/);
  assert.match(contract, /fun targetElapsedNs\(/);
  assert.doesNotMatch(contract, /FireRed|SoulGold|Pokemon|romTitle|romId/i);
});

test('4x/8x/16x renderer budgets can preserve useful states up to 165 Hz without changing game speed', () => {
  const contract = read('app/src/main/java/com/retra/emulator/FastForwardSpeedContract.kt');
  assert.match(contract, /MAX_UNIQUE_PRESENTATION_HZ = 165\.0/);
  assert.match(contract, /floor\(targetFps \/ renderBudgetHz\)/);
});

test('adaptive fallback can only remove more renderer work, never lower the requested multiplier', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  assert.match(policy, /val baseline = speedProtectingCoreFrameSkip/);
  assert.match(policy, /maxOf\(baseline, userFrameSkip/);
});


test('sustained near-target misses trigger speed protection quickly', () => {
  const monitor = read('app/src/main/java/com/retra/emulator/TurboPerformanceMonitor.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(monitor, /utilization < 0\.97/);
  assert.match(monitor, /lowWindows >= 2/);
  assert.match(session, /speed >= 8\.0 -> sample\.utilization < 0\.70/);
});
