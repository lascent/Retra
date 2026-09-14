const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
const monitor = read('app/src/main/java/com/retra/emulator/TurboPerformanceMonitor.kt');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');

test('quality fallback cannot halve 4x 8x or 16x native visual cadence', () => {
  assert.match(policy, /fun smoothFramesPerSlice[\s\S]*framesPerSlice\(speed, frameTimeNs\)/);
  assert.doesNotMatch(policy, /fun smoothFramesPerSlice[\s\S]{0,200}baseline \* 2/);
  assert.match(session, /TurboFramePolicy\.framesPerSlice\(speed, FRAME_TIME_NS\)/);
});

test('extreme turbo VSync synchronization is independent from fallback state', () => {
  const syncFn = policy.match(/fun canSynchronizeExtremeTurboBatchToDisplay[\s\S]*?\n    \}/)?.[0] || '';
  assert.doesNotMatch(syncFn, /constrained/);
  assert.doesNotMatch(session, /constrained = adaptiveQualityFallbackEngaged/);
  assert.match(session, /syncExtremeToDisplay -> true/);
});

test('transient Android jitter cannot trigger quality fallback within half a second', () => {
  assert.match(monitor, /utilization < 0\.97/);
  assert.match(monitor, /lowWindows >= 2/);
  assert.match(monitor, /SAMPLE_WINDOW_NS = 250_000_000L/);
});

test('fallback no longer destroys VSync phase on engage or recovery', () => {
  const constrainedBlock = session.match(/if \(turbo && sample\.constrained\)[\s\S]*?shouldProtectSpeedByMutingAudio/)?.[0] || '';
  assert.doesNotMatch(constrainedBlock, /governorAlignedToVsync = false/);
  assert.doesNotMatch(constrainedBlock, /nextVideoDeadlineNs = System\.nanoTime/);

  const recoveryBlock = session.match(/sample\.recovered && adaptiveQualityFallbackEngaged[\s\S]*?\n                    \}/)?.[0] || '';
  assert.doesNotMatch(recoveryBlock, /governorAlignedToVsync = false/);
  assert.doesNotMatch(recoveryBlock, /turboBatchSequencer\.reset/);
});
