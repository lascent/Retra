const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('all fast-forward multipliers share one cumulative wall-clock governor', () => {
  assert.match(session, /val turbo = speed > 1\.0/);
  assert.match(session, /if \(turbo && successful\)[\s\S]*turboGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
  assert.match(governor, /completedFrames \+= batchFrames\.toLong\(\)/);
  assert.match(governor, /targetNs = epochNs \+/);
});

test('visible slice density is derived only from speed and stable presentation cadence', () => {
  assert.match(session, /val turboPresentationHz = if \(gameplayPresentationHz >= 100f\) 120\.0 else 60\.0/);
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
  assert.doesNotMatch(session, /TurboPerformanceMonitor|adaptiveCoreFrameSkip|sample\.constrained/);
});

test('user frameskip remains authoritative during turbo', () => {
  assert.match(session, /userFrameSkip = prefs\.getInt\(FRAME_SKIP_PREF, 0\)/);
  assert.match(session, /setCoreConfigOption\("frameskip", userFrameSkip\.toString\(\)\)/);
  assert.doesNotMatch(session, /speedProtectingCoreFrameSkip|adaptiveCoreFrameSkip/);
});

test('native turbo advances every hidden CPU frame and only copies the final visible state', () => {
  const turbo = native.match(/Java_com_retra_emulator_MainActivity_runTurboSlice[\s\S]*?return JNI_TRUE;\n\}/)?.[0] || '';
  assert.match(turbo, /for \(int i = 0; i < frames; \+\+i\)/);
  assert.match(turbo, /core->runFrame\(core\);/);
  assert.match(turbo, /GetPrimitiveArrayCritical/);
});

test('turbo audio stays audible at every selected multiplier', () => {
  assert.match(session, /discardAudio = false/);
  assert.match(session, /audioController\.pump\(speed = speed, flushOutput = true\)/);
  assert.doesNotMatch(session, /setTurboMuted\(true\)|shouldProtectSpeedByMutingAudio/);
});
