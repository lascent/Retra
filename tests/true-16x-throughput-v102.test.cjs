const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('true 16x uses the cumulative turbo governor rather than the normal frame pacer', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const governor = read('app/src/main/java/com/retra/emulator/TurboThroughputGovernor.kt');
  assert.match(session, /val turbo = speed > 1\.0/);
  assert.match(session, /if \(turbo && successful\)[\s\S]*turboGovernor\.onBatchComplete/);
  assert.match(governor, /core runs continuously/i);
});

test('16x can use an eight-frame 120 Hz slice or sixteen-frame 60 Hz slice', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const native = read('app/src/main/cpp/native-lib.cpp');
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
  assert.match(read('app/src/main/java/com/retra/emulator/TurboSlicePlanner.kt'), /MAX_NATIVE_BATCH_FRAMES = 16/);
  assert.match(native, /std::min\(16, static_cast<int>\(frameCount\)\)/);
});
