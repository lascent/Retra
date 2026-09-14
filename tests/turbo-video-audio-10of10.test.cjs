const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
const sequencer = read('app/src/main/java/com/retra/emulator/TurboBatchSequencer.kt');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('4x, 8x and 16x can phase-align native batches to high-refresh VSync without changing the speed governor', () => {
  assert.match(policy, /canSynchronizeExtremeTurboBatchToDisplay/);
  assert.match(policy, /speed < 4\.0/);
  assert.match(sequencer, /exactFrames = \(baseFps \* speed\) \/ refreshRateHz/);
  assert.match(session, /syncExtremeToDisplay/);
  assert.match(session, /extremeGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
});

test('a completed turbo image is published before audio and performance bookkeeping', () => {
  const publish = session.indexOf('gameplayFramePresenter.requestPresent()');
  const audioPump = session.indexOf('audioController.pump(', publish);
  const monitor = session.indexOf('throughputMonitor.onFramesCompleted', publish);
  assert.ok(publish >= 0);
  assert.ok(audioPump > publish);
  assert.ok(monitor > publish);
});

test('fast-forward audio restores v1.0.1 raw PCM plus integer averaging instead of the speed-aware FIR path', () => {
  assert.match(main, /external fun readAudioSamplesAtSpeed\(buffer: ShortArray, speed: Double\): Int/);
  assert.match(audio, /readSamples\(nativeScratch\)/);
  assert.match(audio, /appendSpeedAdjusted\(nativeScratch, count\)/);
  assert.match(audio, /appendTurboAveraged/);
  assert.doesNotMatch(audio, /readSamplesAtSpeed\(nativeScratch/);
  assert.match(native, /RETRA_RESAMPLER_TAPS = 32/);
});

test('turbo audio uses the steadier v1.0.1 buffering profile and coalesces frame-sized packets', () => {
  assert.doesNotMatch(audio, /setPerformanceMode\(AudioTrack\.PERFORMANCE_MODE_LOW_LATENCY\)/);
  assert.match(audio, /TRACK_BUFFER_MS = 80/);
  assert.match(audio, /PREBUFFER_MS = 32/);
  assert.match(audio, /TURBO_PACKET_MS = 16/);
  assert.match(audio, /flushPendingOutputIfReady\(TURBO_PACKET_MS\)/);
});

test('measured throughput can sacrifice turbo audio before sacrificing the selected game-speed target', () => {
  assert.match(session, /sample\.utilization < 0\.70/);
  assert.match(session, /audioController\.setTurboMuted\(true\)/);
  assert.match(session, /discardAudio = turboAudioMuted/);
  assert.match(session, /sample\.recovered[\s\S]*audioController\.setTurboMuted\(false\)/);
  assert.match(native, /discardAudio == JNI_TRUE[\s\S]*mAudioBufferRead\(audio, nullptr, available\)/);
});
