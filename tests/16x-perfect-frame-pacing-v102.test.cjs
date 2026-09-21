const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('16x keeps cumulative timing while re-anchoring only after a substantial hitch', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
  assert.match(session, /turboGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
  assert.match(governor, /completedFrames \+= batchFrames\.toLong\(\)/);
  assert.match(governor, /targetNs = epochNs \+/);
  assert.match(governor, /lateByNs > recoveryThresholdNs/);
  assert.match(governor, /completedFrames = 0L/);
});

test('16x uses fractional display-sized native slices rather than a queue of visible frames', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const planner = read('app/src/main/java/com/retra/emulator/TurboSlicePlanner.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
  assert.match(planner, /targetFps \/ usefulSliceHz/);
  assert.match(planner, /MAX_NATIVE_BATCH_FRAMES = 16/);
  assert.match(session, /runTurboSlice\([\s\S]*captureVideo = true/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
  assert.match(native, /Convert only the final frame in the turbo slice/);
});

test('turbo never switches the panel into 144 or 165 Hz modes', () => {
  const display = read('app/src/main/java/com/retra/emulator/DisplayPerformanceManager.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(display, /maxTurboRefreshRateHz: Float = 120f/);
  assert.match(session, /coerceAtMost\(120f\)/);
  assert.match(session, /applyGameplayMode\(extremeTurbo = false\)/);
  assert.doesNotMatch(session, /extremePresentationHz|syncExtremeToDisplay/);
});

test('latest-frame mailbox drops stale pending frames instead of building a turbo backlog', () => {
  const mailbox = read('app/src/main/java/com/retra/emulator/GameplayFrameMailbox.kt');
  assert.match(mailbox, /pending\?\.let \{ stale/);
  assert.match(mailbox, /pending = completed/);
  assert.match(mailbox, /freeBuffers\.addLast\(stale\)/);
  assert.match(mailbox, /acquireLatestForRender/);
});
