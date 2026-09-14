const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('8x and 16x use lower-overhead native batches', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(policy, /MAX_TURBO_BATCH_FRAMES = 16/);
  assert.match(policy, /speed \/ 2\.0/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
});

test('turbo restores the v1.0.1 raw-PCM averaging path and keeps a cheap native mute fallback', () => {
  const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(activity, /external fun readAudioSamplesAtSpeed\(buffer: ShortArray, speed: Double\): Int/);
  assert.match(audio, /readSamples\(nativeScratch\)/);
  assert.match(audio, /appendSpeedAdjusted\(nativeScratch, count\)/);
  assert.match(audio, /appendTurboAveraged/);
  assert.doesNotMatch(audio, /readSamplesAtSpeed\(nativeScratch/);
  assert.match(session, /discardAudio = turboAudioMuted/);
  assert.match(native, /discardAudio == JNI_TRUE/);
  assert.match(native, /mAudioBufferRead\(audio, nullptr, available\)/);
});

test('frontend exclusively owns pacing so mGBA sync cannot cap turbo', () => {
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(native, /core->opts\.videoSync = false;/);
  assert.match(native, /core->opts\.audioSync = false;/);
});

test('renderer skipping is adaptive rather than forced immediately at 8x', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(policy, /fun coreFrameSkip[\s\S]*userFrameSkip\.coerceIn/);
  assert.match(policy, /fun constrainedCoreFrameSkip/);
  assert.match(session, /TurboPerformanceMonitor/);
  assert.match(session, /sample\.constrained/);
});

test('smoothness remains independently VSync paced during 4x, 8x and 16x', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const presenter = read('app/src/main/java/com/retra/emulator/GameplayFramePresenter.kt');
  assert.match(session, /setContinuousVsync\(speed >= 4\.0\)/);
  assert.match(presenter, /presentLatestFrame\(\)/);
  assert.match(presenter, /scheduleNextVsyncDirect\(\)/);
});


test('Android 12+ receives workload hints from the emulation worker', () => {
  const hints = read('app/src/main/java/com/retra/emulator/EmulationPerformanceHints.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(hints, /PerformanceHintManager/);
  assert.match(hints, /reportActualWorkDuration/);
  assert.match(session, /performanceHints\.report/);
});
