const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const pacer = read('app/src/main/java/com/retra/emulator/EmulationFramePacer.kt');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
const native = read('app/src/main/cpp/native-lib.cpp');

test('frame pacer learns Android scheduler oversleep without expanding the bounded spin ceiling', () => {
  assert.match(pacer, /schedulerOversleepEstimateNanos/);
  assert.match(pacer, /adaptivePrecisionWindow/);
  assert.match(pacer, /MAX_PRECISION_WINDOW_NS = 300_000L/);
  assert.match(pacer, /schedulerOversleepEstimateNanos \* 3L \+ oversleep/);
});

test('native-speed pacing re-anchors after a substantial hitch instead of burst-catching up', () => {
  assert.match(pacer, /SMOOTH_RECOVERY_NUMERATOR = 3L/);
  assert.match(pacer, /SMOOTH_RECOVERY_DENOMINATOR = 4L/);
  assert.match(pacer, /nextDeadlineNanos = now/);
});

test('Android performance hints include audio and gameplay bookkeeping in the reported frame cost', () => {
  assert.match(session, /val totalWorkNs = \(System\.nanoTime\(\) - workStartedNs\)/);
  assert.match(session, /performanceHints\.report\([\s\S]*actualWorkNs = totalWorkNs/);
});

test('steady-state gameplay reuses audio and rewind buffers to reduce allocation jitter', () => {
  assert.match(audio, /outputPacketPool = ArrayDeque<ShortArray>\(\)/);
  assert.match(audio, /acquireOutputPacket\(alignedCount\)/);
  assert.match(audio, /recycleOutputPacket\(packet\)/);
  assert.match(native, /rewindSpareBuffers/);
  assert.match(native, /acquireRewindBufferLocked/);
  assert.match(native, /recycleRewindBufferLocked/);
});
