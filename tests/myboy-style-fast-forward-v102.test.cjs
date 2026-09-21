const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('fast-forward uses display-sized native batches and latest-frame GPU presentation', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const view = read('app/src/main/java/com/retra/emulator/ShaderGameView.kt');
  const mailbox = read('app/src/main/java/com/retra/emulator/GameplayFrameMailbox.kt');
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
  assert.match(session, /gameplayFrameMailbox\.publish/);
  assert.match(view, /mailbox\.acquireLatestForRender\(reusableMailboxFrame\)/);
  assert.match(mailbox, /pending\?\.let \{ stale/);
});

test('turbo audio stays active and independent from video presentation', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
  assert.match(session, /discardAudio = false/);
  assert.match(session, /audioController\.pump\(speed = speed, flushOutput = true\)/);
  assert.match(audio, /Thread\(::audioWriterLoop, "Retra-Audio"\)/);
  assert.doesNotMatch(session, /setTurboMuted\(true\)/);
});

test('frontend exclusively owns pacing so mGBA sync cannot cap turbo', () => {
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(native, /core->opts\.videoSync = false;/);
  assert.match(native, /core->opts\.audioSync = false;/);
});

test('Android 12+ receives workload hints from the emulation worker', () => {
  const hints = read('app/src/main/java/com/retra/emulator/EmulationPerformanceHints.kt');
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  assert.match(hints, /PerformanceHintManager/);
  assert.match(hints, /reportActualWorkDuration/);
  assert.match(session, /performanceHints\.report/);
});
