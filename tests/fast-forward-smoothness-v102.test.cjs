const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('turbo batches hidden core frames behind one JNI boundary and converts only the final frame', () => {
  const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(activity, /external fun runTurboSlice/);
  assert.match(native, /Java_com_retra_emulator_MainActivity_runTurboSlice/);
  assert.match(native, /Convert only the final frame in the turbo slice/);
  assert.match(session, /runTurboSlice\([\s\S]*captureVideo = true[\s\S]*discardAudio = false/);
});

test('turbo uses a cumulative governor so scheduler oversleep does not lower selected speed', () => {
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(governor, /completedFrames \+= batchFrames\.toLong\(\)/);
  assert.match(governor, /targetNs = epochNs \+/);
  assert.match(session, /turboGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
});

test('visible fast-forward frames are latest-only and never queued', () => {
  const mailbox = read('app/src/main/java/com/retra/emulator/GameplayFrameMailbox.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(session, /gameplayFrameMailbox\.publishLatest\(framePixels, framePublishResult\)/);
  assert.match(mailbox, /Older pending frames are recycled instead of queued/);
  assert.match(mailbox, /pending = completed/);
  assert.match(mailbox, /acquireLatestForRender/);
});

test('Speed Mode does not change mGBA renderer frameskip behind the user', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(session, /Keep the user's configured value authoritative/);
  assert.doesNotMatch(session, /TurboPerformanceMonitor|adaptiveCoreFrameSkip|speedProtectingCoreFrameSkip/);
});

test('presentation stays on one cadence-compatible 60 or 120 Hz mode for the whole session', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(session, /turboPresentationHz = if \(gameplayPresentationHz >= 100f\) 120\.0 else 60\.0/);
  assert.match(session, /applyGameplayMode\(extremeTurbo = false\)/);
  assert.doesNotMatch(session, /syncExtremeToDisplay|extremePresentationHz/);
});
