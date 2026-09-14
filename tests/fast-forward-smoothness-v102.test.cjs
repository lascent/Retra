const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('turbo batches hidden core frames behind one JNI boundary and converts only useful final frames', () => {
  const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');

  assert.match(activity, /external fun runTurboSlice\([\s\S]*discardAudio: Boolean[\s\S]*\): Boolean/);
  assert.match(native, /Java_com_retra_emulator_MainActivity_runTurboSlice/);
  const turbo = native.match(/Java_com_retra_emulator_MainActivity_runTurboSlice[\s\S]*?return JNI_TRUE;\n\}/)?.[0] || '';
  assert.match(turbo, /for \(int i = 0; i < frames; \+\+i\)/);
  assert.match(turbo, /core->runFrame\(core\);/);
  assert.match(turbo, /if \(captureVideo != JNI_TRUE\)[\s\S]*return JNI_TRUE;/);
  assert.match(turbo, /GetPrimitiveArrayCritical/);
  assert.match(session, /runTurboSlice\([\s\S]*discardAudio = turboAudioMuted[\s\S]*\)/);
});

test('16x uses lower-overhead native batches while presentation remains independently VSync paced', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');

  assert.match(policy, /MAX_TURBO_BATCH_FRAMES = 16/);
  assert.match(policy, /speed \/ 2\.0/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
  assert.match(native, /setAudioBufferSize\(core, 8192\)/);
});

test('8x/16x use a cumulative throughput governor so scheduler oversleep does not lower selected speed', () => {
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');

  assert.match(governor, /completedFrames \+= frameCount/);
  assert.match(governor, /targetNs = epochNs \+/);
  assert.match(governor, /LockSupport\.parkNanos/);
  assert.match(governor, /SPIN_WINDOW_NS = 250_000L/);
  assert.match(session, /turbo && successful/);
  assert.match(session, /extremeGovernor\.onBatchComplete\(speed, framesThisSlice\)/);
  assert.match(session, /else \{[\s\S]*framePacer\.waitForNext/);
});

test('extreme turbo uses mGBA renderer frameskip only to remove invisible video work', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');

  assert.match(policy, /speed >= 16\.0 -> 7/);
  assert.match(policy, /speed >= 8\.0 -> 3/);
  assert.match(policy, /fun constrainedCoreFrameSkip/);
  assert.match(policy, /maxOf\(userFrameSkip\.coerceIn\(0, 10\), fallback\)/);
  assert.match(session, /sample\.constrained/);
  assert.match(session, /setCoreConfigOption\("frameskip", desiredCoreFrameSkip\.toString\(\)\)/);
  assert.match(session, /setCoreConfigOption\("frameskip", userFrameSkip\.toString\(\)\)/);
});

test('4x/8x/16x presentation follows the selected high-refresh panel cadence independently of emulation speed', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');

  assert.doesNotMatch(policy, /speed >= 8\.0\) 60f/);
  assert.match(policy, /1_000_000_000\.0 \/ panelHz\.toDouble\(\)/);
  assert.match(session, /presentationNowNs = System\.nanoTime\(\)/);
  assert.match(session, /presentationNowNs >= nextVideoDeadlineNs/);
  assert.match(session, /gameplayFramePresenter\.requestPresent\(\)/);
  assert.match(session, /gameplayFramePresenter\.setContinuousVsync\(speed >= 4\.0\)/);
});

test('extreme turbo receives display-class scheduler priority while rendering stays separately capped', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');

  assert.match(policy, /speed >= 8\.0 -> Process\.THREAD_PRIORITY_DISPLAY/);
  assert.match(session, /Process\.setThreadPriority\(TurboFramePolicy\.processThreadPriority\(speed, cpuProfile\)\)/);
});
