const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const native = fs.readFileSync(path.join(root, 'app/src/main/cpp/native-lib.cpp'), 'utf8');

test('distortion fix uses a normalized polyphase FIR with anti-alias cutoff', () => {
  assert.match(native, /RETRA_RESAMPLER_TAPS = 16/);
  assert.match(native, /RETRA_RESAMPLER_PHASES = 1024/);
  assert.match(native, /Blackman-windowed low-pass sinc/);
  assert.match(native, /ratio < 1\.0 \? std::max\(0\.05, ratio \* 0\.94\) : 1\.0/);
  assert.match(native, /weightSum/);
  assert.match(native, /coefficient = static_cast<float>\(coefficient \/ weightSum\)/);
});

test('distortion fix is saturation-safe and keeps exact-rate PCM bit-transparent', () => {
  assert.match(native, /retraClampPcm16/);
  assert.match(native, /return 32767/);
  assert.match(native, /return -32768/);
  assert.match(native, /sourceRate == destinationRate[\s\S]*mAudioBufferRead\(source, output, maxFrames\)/);
});

test('old unchecked mGBA sinc conversion is no longer in Retra output path', () => {
  assert.doesNotMatch(native, /audio-resampler\.h/);
  assert.doesNotMatch(native, /mINTERPOLATOR_SINC/);
  assert.doesNotMatch(native, /mAudioResamplerProcess/);
});
