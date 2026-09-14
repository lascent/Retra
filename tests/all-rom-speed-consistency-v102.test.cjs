const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
const monitor = read('app/src/main/java/com/retra/emulator/TurboPerformanceMonitor.kt');
const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('all fast-forward multipliers share the same cumulative wall-clock governor', () => {
  assert.match(session, /if \(turbo && successful\)[\s\S]*extremeGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
  assert.match(governor, /every fast-forward multiplier/);
  assert.match(governor, /completedFrames \+= frameCount/);
  assert.match(governor, /targetNs = epochNs \+/);
});

test('throughput monitoring applies to every turbo speed without reacting to brief scheduler noise', () => {
  assert.match(monitor, /if \(speed > 1\.0\)/);
  assert.match(monitor, /utilization < 0\.97/);
  assert.match(monitor, /lowWindows >= 2/);
  assert.match(monitor, /utilization >= 0\.995/);
  assert.match(monitor, /healthyWindows >= 2/);
  assert.match(monitor, /achievedMultiplier = actualFps \/ baseFps/);
  assert.match(session, /if \(turbo && sample\.constrained\)/);
});

test('heavy ROM fallback never doubles turbo batches and therefore never halves fresh visual cadence', () => {
  assert.match(policy, /fun smoothFramesPerSlice/);
  assert.match(policy, /framesPerSlice\(speed, frameTimeNs\)/);
  assert.doesNotMatch(policy, /baseline \* 2/);
  assert.match(session, /TurboFramePolicy\.framesPerSlice\(speed, FRAME_TIME_NS\)/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
});

test('8x and 16x keep display-synchronized fractional batching even while quality fallback is engaged', () => {
  assert.match(policy, /fun canSynchronizeExtremeTurboBatchToDisplay\([\s\S]*refreshRateHz: Float\n    \): Boolean/);
  assert.doesNotMatch(policy, /constrained: Boolean/);
  assert.doesNotMatch(session, /constrained = adaptiveQualityFallbackEngaged/);
  assert.match(session, /Do not break display synchronization here/);
});

test('renderer adaptation is mild and reserved for severe sustained misses', () => {
  assert.match(policy, /speed >= 16\.0 && utilization < 0\.72 -> 3/);
  assert.match(policy, /speed >= 8\.0 && utilization < 0\.72 -> 2/);
  assert.match(policy, /speed >= 4\.0 && utilization < 0\.70 -> 1/);
  assert.doesNotMatch(policy, /speed >= 2\.0 && utilization/);
});

test('v1.0.1-style turbo audio is preserved until a severe measured miss threatens exact speed', () => {
  assert.match(session, /speed >= 16\.0 -> sample\.utilization < 0\.78/);
  assert.match(session, /speed >= 8\.0 -> sample\.utilization < 0\.70/);
  assert.match(session, /speed >= 4\.0 -> sample\.utilization < 0\.62/);
  assert.match(session, /else -> false/);
  assert.match(session, /shouldProtectSpeedByMutingAudio/);
});
