const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('completed video is published before audio and performance bookkeeping', () => {
  const publish = session.indexOf('gameplayFrameMailbox.publishLatest(framePixels, framePublishResult)');
  const request = session.indexOf('gameplayFramePresenter.requestPresent(framePublishResult.generation)', publish);
  const audioPump = session.indexOf('audioController.pump(', request);
  const hints = session.indexOf('performanceHints.report(', audioPump);
  assert.ok(publish >= 0);
  assert.ok(request > publish);
  assert.ok(audioPump > request);
  assert.ok(hints > audioPump);
});

test('fast-forward audio uses native speed-aware FIR so raw 16x PCM does not cross JNI', () => {
  assert.match(main, /external fun readAudioSamplesAtSpeed\(buffer: ShortArray, speed: Double\): Int/);
  assert.match(main, /readSamplesAtSpeed = \{ buffer, speed -> readAudioSamplesAtSpeed\(buffer, speed\) \}/);
  assert.match(audio, /readSamplesAtSpeed\(nativeScratch, normalizedSpeed\)/);
  assert.match(audio, /nativeTurboResampling/);
  assert.match(audio, /appendRaw\(nativeScratch, count\)/);
  assert.match(native, /RETRA_RESAMPLER_TAPS = 32/);
  assert.match(native, /normalizedSpeed/);
});

test('turbo audio is never sacrificed to protect renderer throughput', () => {
  assert.match(session, /discardAudio = false/);
  assert.doesNotMatch(session, /setTurboMuted\(true\)|shouldProtectSpeedByMutingAudio/);
});
