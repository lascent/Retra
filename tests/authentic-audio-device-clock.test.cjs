const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('high-quality audio targets the Android native output clock to avoid a second OS resample', () => {
  assert.match(audio, /AudioTrack\.getNativeOutputSampleRate\(AudioManager\.STREAM_MUSIC\)/);
  assert.match(audio, /requestedRate != HIGH_QUALITY_SOURCE_RATE/);
  assert.match(audio, /onOutputRateChanged\(track\.sampleRate\.coerceIn\(8000, 96000\)\)/);
  assert.match(main, /setCoreConfigOption\("retra\.outputSampleRate", rate\.toString\(\)\)/);
  assert.match(native, /runtimeConfigOptions\.find\("retra\.outputSampleRate"\)/);
  assert.match(native, /resampleRetraAudioLocked\([\s\S]*destinationRate/);
});

test('audio buffering adapts upward only after real underruns and relaxes after stable playback', () => {
  assert.match(audio, /BASE_PREBUFFER_MS = PREBUFFER_MS/);
  assert.match(audio, /MAX_PREBUFFER_MS = 64/);
  assert.match(audio, /BASE_TRACK_BUFFER_MS = TRACK_BUFFER_MS/);
  assert.match(audio, /MAX_TRACK_BUFFER_MS = 112/);
  assert.match(audio, /noteUnderrun\(track\)/);
  assert.match(audio, /relaxAdaptiveBufferingIfStable\(track\)/);
  assert.match(audio, /track\.setBufferSizeInFrames/);
  assert.match(audio, /STABLE_RELAX_AFTER_NS = 30_000_000_000L/);
});

test('authentic path uses saturating band-limited resampling and 16-bit PCM without enhancement DSP', () => {
  assert.match(native, /RETRA_RESAMPLER_TAPS = 32/);
  assert.match(native, /retraNormalizedSinc/);
  assert.match(native, /retraClampPcm16/);
  assert.match(native, /sourceRate == destinationRate[\s\S]*mAudioBufferRead/);
  assert.doesNotMatch(native, /mINTERPOLATOR_SINC|mAudioResamplerProcess/);
  assert.match(audio, /AudioFormat\.ENCODING_PCM_16BIT/);
  assert.doesNotMatch(audio, /BassBoost|Equalizer|Virtualizer|LoudnessEnhancer/);
});
