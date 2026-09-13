const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const native = fs.readFileSync(path.join(root, 'app/src/main/cpp/native-lib.cpp'), 'utf8');

test('authentic HQ output removes DC while preserving audible bass', () => {
  assert.match(native, /RETRA_DC_BLOCK_HZ = 18\.0/);
  assert.match(native, /dcCoefficient = std::exp/);
  assert.match(native, /inputLeft - state\.previousInputLeft \+/);
  assert.match(native, /inputRight - state\.previousInputRight \+/);
});

test('authentic HQ output uses gentle mGBA-style smoothing instead of heavy EQ', () => {
  assert.match(native, /RETRA_SMOOTHING_STRENGTH = 0\.22/);
  assert.match(native, /state\.smoothedLeft = state\.smoothedLeft \* smoothingA \+ dcLeft \* smoothingB/);
  assert.match(native, /state\.smoothedRight = state\.smoothedRight \* smoothingA \+ dcRight \* smoothingB/);
  assert.doesNotMatch(native, /RETRA_SMOOTHING_STRENGTH = 0\.60/);
});

test('authentic HQ conditioning keeps PCM16 headroom and runs after rate conversion', () => {
  assert.match(native, /RETRA_OUTPUT_HEADROOM = 0\.99/);
  assert.match(native, /conditionRetraAudioLocked\([\s\S]*destinationRate[\s\S]*produced\)/);
  assert.match(native, /retraClampPcm16\(state\.smoothedLeft \* RETRA_OUTPUT_HEADROOM\)/);
  assert.match(native, /retraClampPcm16\(state\.smoothedRight \* RETRA_OUTPUT_HEADROOM\)/);
});
