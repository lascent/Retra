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
const shaderController = read('app/src/main/java/com/retra/emulator/ShaderController.kt');
const multiplayer = read('app/src/main/java/com/retra/emulator/MultiplayerController.kt');
const display = read('app/src/main/java/com/retra/emulator/DisplayPerformanceManager.kt');
const planner = read('app/src/main/java/com/retra/emulator/TurboSlicePlanner.kt');

test('latest-frame mailbox preallocates four buffers with a spare and no hot-path array allocation', () => {
  assert.match(mailbox, /val producer = IntArray\(pixelCount\)/);
  assert.match(mailbox, /TOTAL_FRAME_BUFFERS = 4/);
  assert.match(mailbox, /repeat\(TOTAL_FRAME_BUFFERS - 1\)/);
  const hotPublish = mailbox.slice(mailbox.indexOf('fun publishLatest('), mailbox.indexOf('/** Compatibility helper for non-hot-path callers\/tests. *\/'));
  assert.doesNotMatch(hotPublish, /IntArray\(pixelCount\)/);
  assert.match(mailbox, /pending\?\.let \{ stale/);
  assert.match(mailbox, /freeBuffers\.addLast\(stale\)/);
});

test('emulator publishes mailbox generation before audio and requests that exact VSync generation', () => {
  const publish = session.indexOf('gameplayFrameMailbox.publishLatest(framePixels, framePublishResult)');
  const request = session.indexOf('gameplayFramePresenter.requestPresent(framePublishResult.generation)', publish);
  const audio = session.indexOf('audioController.pump(', request);
  assert.ok(publish >= 0);
  assert.ok(request > publish);
  assert.ok(audio > request);
  assert.match(presenter, /updatePendingGeneration\(generation\)/);
  assert.match(presenter, /pendingGeneration\.compareAndSet\(current, generation\)/);
});

test('GLSurfaceView consumes the newest mailbox frame on its render thread', () => {
  assert.match(shaderView, /mailbox\.acquireLatestForRender\(reusableMailboxFrame\)/);
  assert.match(shaderView, /uploadPixels\([\s\S]*reusableMailboxFrame\.pixels/);
  assert.match(shaderView, /RENDERMODE_WHEN_DIRTY/);
  assert.match(shaderView, /never on Android's UI\/Choreographer thread/);
});

test('normal gameplay uses pass-through GL and ImageView remains fallback-only', () => {
  assert.match(shaderController, /Normal gameplay also uses the dedicated GL surface/);
  assert.match(shaderController, /normalView\.visibility = View\.INVISIBLE/);
  assert.match(shaderController, /shaderView\.visibility = View\.VISIBLE/);
  assert.match(shaderController, /shaderView\.usePassthrough/);
  assert.match(multiplayer, /OEM compatibility fallback if the GLSurfaceView could not be created/);
});

test('Speed Mode uses stable 60 or 120 Hz continuous VSync sampling with fractional native batches', () => {
  assert.match(session, /turboPresentationHz = if \(gameplayPresentationHz >= 100f\) 120\.0 else 60\.0/);
  assert.match(session, /setPresentationFrameRate\(turboPresentationHz\.toFloat\(\)\)/);
  assert.match(session, /setContinuousVsync\(turbo\)/);
  assert.match(session, /turboSlicePlanner\.nextFrames\(\)/);
  assert.match(planner, /framesPerSliceExact/);
  assert.match(planner, /phase \+= framesPerSliceExact/);
  assert.match(display, /maxTurboRefreshRateHz: Float = 120f/);
});
