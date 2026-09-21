const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('turbo publishes fractional display-sized native batches while retaining the 16-frame defensive cap', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const planner = read('app/src/main/java/com/retra/emulator/TurboSlicePlanner.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
  assert.match(planner, /phase \+= framesPerSliceExact/);
  assert.match(planner, /MAX_NATIVE_BATCH_FRAMES = 16/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
});

test('Speed Mode keeps a Choreographer callback armed and samples only changed generations', () => {
  const presenter = read('app/src/main/java/com/retra/emulator/GameplayFramePresenter.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(presenter, /Choreographer\.getInstance\(\)\.postFrameCallback\(this\)/);
  assert.match(presenter, /generation != presentedGeneration/);
  assert.match(session, /gameplayFramePresenter\.requestPresent\(framePublishResult\.generation\)/);
  assert.match(session, /gameplayFramePresenter\.setContinuousVsync\(turbo\)/);
});

test('fast-forward worker yields scheduling priority to Android rendering', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(session, /turbo -> Process\.THREAD_PRIORITY_MORE_FAVORABLE/);
  assert.doesNotMatch(session, /turbo -> Process\.THREAD_PRIORITY_URGENT_DISPLAY/);
});
