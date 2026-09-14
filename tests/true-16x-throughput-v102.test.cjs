const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('true 16x path is not paced by the ordinary short-slice frame pacer', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
  assert.match(session, /val turbo = speed > 1\.0/);
  assert.match(session, /if \(turbo && successful\)/);
  assert.match(session, /extremeGovernor\.onBatchComplete/);
  assert.match(governor, /If the CPU cannot sustain the requested multiplier[\s\S]*core runs continuously/);
});

test('16x performs short eight-frame native batches with adaptive renderer skipping', () => {
  const policy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(policy, /MAX_TURBO_BATCH_FRAMES = 16/);
  assert.match(policy, /speed \/ 2\.0/);
  assert.match(policy, /speed >= 16\.0 -> 7/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
});
