const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const mailbox = read('app/src/main/java/com/retra/emulator/GameplayFrameMailbox.kt');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const presenter = read('app/src/main/java/com/retra/emulator/GameplayFramePresenter.kt');
const shaderView = read('app/src/main/java/com/retra/emulator/ShaderGameView.kt');
const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');

test('final smooth-turbo mailbox keeps four preallocated pixel buffers and drops instead of allocating on starvation', () => {
  assert.match(mailbox, /TOTAL_FRAME_BUFFERS = 4/);
  assert.match(mailbox, /repeat\(TOTAL_FRAME_BUFFERS - 1\)/);
  assert.match(mailbox, /drop this visual publish and keep emulation progressing safely/);
  const hotPublishStart = mailbox.indexOf('fun publishLatest(\n        completed: IntArray,\n        out: PublishResult');
  const hotPublishEnd = mailbox.indexOf('/** Compatibility helper for non-hot-path callers/tests. */', hotPublishStart);
  assert.ok(hotPublishStart >= 0 && hotPublishEnd > hotPublishStart);
  assert.doesNotMatch(mailbox.slice(hotPublishStart, hotPublishEnd), /IntArray\(/);
});

test('producer and GL consumer reuse mailbox result holders on the gameplay hot path', () => {
  assert.match(session, /val framePublishResult = GameplayFrameMailbox\.PublishResult\(\)/);
  assert.match(session, /publishLatest\(framePixels, framePublishResult\)/);
  assert.match(shaderView, /reusableMailboxFrame = GameplayFrameMailbox\.RenderFrame\(\)/);
  assert.match(shaderView, /acquireLatestForRender\(reusableMailboxFrame\)/);
});

test('speed changes reset turbo pacing and audio time-scale state before the first new-speed frame', () => {
  const speedChange = session.match(/if \(kotlin\.math\.abs\(speed - lastSpeed\)[\s\S]*?lastSpeed = speed/);
  assert.ok(speedChange);
  assert.match(speedChange[0], /framePacer\.reset\(\)/);
  assert.match(speedChange[0], /turboGovernor\.reset\(\)/);
  assert.match(speedChange[0], /turboSlicePlanner\.reset\(speed, FRAME_TIME_NS, turboPresentationHz\)/);
  assert.match(speedChange[0], /audioController\.onSpeedChanged\(speed\)/);
  assert.match(audio, /fun onSpeedChanged\(speed: Double\)[\s\S]*prepareTransform\(normalizeSpeed\(speed\)\)/);
});

test('new gameplay sessions reset presenter generation state so the first frame cannot be mistaken for an old generation', () => {
  const start = presenter.match(/fun start\(\) \{[\s\S]*?active = true\n    \}/);
  assert.ok(start);
  assert.match(start[0], /pendingGeneration\.set\(0L\)/);
  assert.match(start[0], /presentedGeneration = -1L/);
  assert.match(start[0], /latestVsyncTimeNs\.set\(0L\)/);
});

test('VSync scheduling reuses posted Runnables and updates generations with CAS instead of accumulate lambdas', () => {
  assert.match(presenter, /postFrameCallbackRunnable = Runnable/);
  assert.match(presenter, /removeFrameCallbackRunnable = Runnable/);
  assert.match(presenter, /targetView\.post\(postFrameCallbackRunnable\)/);
  assert.match(presenter, /pendingGeneration\.compareAndSet\(current, generation\)/);
  assert.doesNotMatch(presenter, /accumulateAndGet/);
});

