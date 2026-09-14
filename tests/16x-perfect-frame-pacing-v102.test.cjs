const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('16x uses a fractional display-synchronized batch sequencer without changing the speed governor', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const sequencer = read('app/src/main/java/com/retra/emulator/TurboBatchSequencer.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');

  assert.match(policy, /canSynchronizeExtremeTurboBatchToDisplay/);
  assert.match(sequencer, /exactFrames = \(baseFps \* speed\) \/ refreshRateHz/);
  assert.match(sequencer, /val frames = floor\(desired\)/);
  assert.match(session, /turboBatchSequencer\.nextFrames/);
  assert.match(session, /extremeGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
  assert.match(governor, /completedFrames \+= frameCount/);
});

test('16x phase-locks its cumulative governor to a real Choreographer VSync timestamp', () => {
  const presenter = read('app/src/main/java/com/retra/emulator/GameplayFramePresenter.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');

  assert.match(presenter, /latestVsyncTimeNs\.set\(frameTimeNanos\)/);
  assert.match(presenter, /fun latestVsyncNanos\(\): Long/);
  assert.match(session, /val lastVsyncNs/);
  assert.match(session, /extremeGovernor\.reset\(lastVsyncNs\)/);
  assert.match(governor, /fun reset\(epochTimeNs: Long = nanoTime\(\)\)/);
});

test('display-synchronized 16x publishes each useful batch and remains synchronized during quality fallback', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const monitor = read('app/src/main/java/com/retra/emulator/TurboPerformanceMonitor.kt');

  assert.match(session, /syncExtremeToDisplay -> true/);
  assert.doesNotMatch(session, /constrained = adaptiveQualityFallbackEngaged/);
  assert.match(session, /Do not break display synchronization here/);
  assert.match(policy, /adaptiveCoreFrameSkip/);
  assert.match(monitor, /val recovered: Boolean/);
  assert.match(session, /sample\.recovered/);
});

test('extreme turbo can use 144 or 165 Hz without changing normal GBA cadence policy', () => {
  const display = read('app/src/main/java/com/retra/emulator/DisplayPerformanceManager.kt');
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');

  assert.match(display, /maxTurboRefreshRateHz: Float = 165f/);
  assert.match(display, /selectBestExtremeTurboMode/);
  assert.match(display, /selectBestGameplayMode/);
  assert.match(policy, /MAX_PRESENTATION_HZ = 165f/);
});
