const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('extreme turbo keeps native bursts short enough to yield to Android rendering', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(policy, /speed \/ 2\.0/);
  assert.match(policy, /MAX_TURBO_BATCH_FRAMES = 16/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
});

test('120 Hz capable gameplay is not artificially reduced to 60 Hz at 8x or 16x', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  assert.match(policy, /refreshRateHz\.coerceIn\(MIN_PRESENTATION_HZ, MAX_PRESENTATION_HZ\)/);
  assert.doesNotMatch(policy, /effectiveHz = if \(speed >= 8\.0\) 60f/);
});

test('4x and faster turbo hold a direct VSync loop while 1x/2x stay event driven', () => {
  const presenter = read('app/src/main/java/com/retra/emulator/GameplayFramePresenter.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(presenter, /continuousVsync/);
  assert.match(presenter, /scheduleNextVsyncDirect/);
  assert.match(presenter, /Choreographer\.getInstance\(\)\.postFrameCallback\(this\)/);
  assert.match(session, /setContinuousVsync\(speed >= 4\.0\)/);
  assert.match(session, /setContinuousVsync\(false\)/);
});

test('emulation worker does not take urgent-display priority away from RenderThread at extreme turbo', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const extremeBranch = policy.match(/fun processThreadPriority[\s\S]*?\n    \}/)?.[0] || policy;
  assert.match(extremeBranch, /speed >= 8\.0 -> Process\.THREAD_PRIORITY_DISPLAY/);
  assert.doesNotMatch(extremeBranch, /speed >= 16\.0[\s\S]*THREAD_PRIORITY_URGENT_DISPLAY/);
});
