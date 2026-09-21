const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const pacer = read('app/src/main/java/com/retra/emulator/EmulationFramePacer.kt');
const turboPolicy = read('app/src/main/java/com/retra/emulator/TurboFramePolicy.kt');
const display = read('app/src/main/java/com/retra/emulator/DisplayPerformanceManager.kt');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
const audio = read('app/src/main/java/com/retra/emulator/AudioController.kt');
const native = read('app/src/main/cpp/native-lib.cpp');
const html = read('app/src/main/assets/retra/index.html');
const settingsUi = read('app/src/main/assets/retra/settings-ui.js');

test('native-speed gameplay uses an absolute deadline pacer with bounded precision wait', () => {
  assert.match(session, /EmulationFramePacer\(\)/);
  assert.match(session, /framePacer\.waitForNext\([\s\S]*sliceCadenceNs,[\s\S]*TurboFramePolicy\.precisionWindowNs\(speed, normalPrecisionWindow\)/);
  assert.match(pacer, /nextDeadlineNanos \+= frameDurationNanos/);
  assert.match(pacer, /LockSupport\.parkNanos/);
  assert.match(pacer, /MAX_PRECISION_WINDOW_NS = 300_000L/);
  assert.match(turboPolicy, /Process\.THREAD_PRIORITY_DISPLAY/);
  assert.match(turboPolicy, /Process\.THREAD_PRIORITY_URGENT_DISPLAY/);
});

test('completed GBA frames publish into a latest-frame mailbox without a UI-thread copy', () => {
  const mailbox = read('app/src/main/java/com/retra/emulator/GameplayFrameMailbox.kt');
  const shaderView = read('app/src/main/java/com/retra/emulator/ShaderGameView.kt');
  assert.match(session, /gameplayFrameMailbox\.publishLatest\(framePixels, framePublishResult\)/);
  assert.match(mailbox, /pending = completed/);
  assert.match(shaderView, /mailbox\.acquireLatestForRender\(reusableMailboxFrame\)/);
  assert.doesNotMatch(session, /System\.arraycopy\([\s\S]*framePixels/);
});

test('gameplay refresh selection prefers 60-or-120 cadence-compatible modes instead of 90 Hz judder', () => {
  assert.match(display, /selectBestGameplayMode/);
  assert.match(display, /GBA_SOURCE_FPS = 59\.7275f/);
  assert.match(display, /GAMEPLAY_CADENCE_TOLERANCE_HZ/);
  assert.match(display, /cadenceCompatible\.maxByOrNull \{ it\.refreshRate \}/);
  assert.match(display, /applyGameplayMode\(\)/);
});

test('hidden WebView work is suspended during native gameplay without detaching the warm page', () => {
  assert.match(main, /showEmulatorUiKeepingWebWarm\(\)[\s\S]*webView\.visibility = View\.INVISIBLE[\s\S]*webView\.onPause\(\)/);
  assert.match(main, /showWebUiWithoutBlankFrame\(\)[\s\S]*webView\.onResume\(\)[\s\S]*webView\.visibility = View\.VISIBLE/);
});

test('gameplay stays on the normal hardware-accelerated compositor path instead of forced changing layers', () => {
  assert.match(settings, /LAYER_TYPE_NONE else View\.LAYER_TYPE_SOFTWARE/);
  assert.doesNotMatch(settings, /LAYER_TYPE_HARDWARE else View\.LAYER_TYPE_SOFTWARE/);
  assert.match(display, /decor\.setLayerType\(View\.LAYER_TYPE_NONE, null\)/);
});

test('fresh installs default to crisp nearest-neighbor GBA scaling', () => {
  assert.match(settings, /LINEAR_FILTERING_PREF, false/);
  assert.match(settingsUi, /bool\(linearFilteringToggle, 'linearFiltering', false\)/);
  assert.match(html, /id="linearFilteringToggle" type="checkbox"/);
  assert.doesNotMatch(html, /checked="" id="linearFilteringToggle"/);
});

test('audio output cannot block the emulation frame loop and is isolated on an audio-priority writer', () => {
  assert.match(audio, /flushPendingOutput\(\)[\s\S]*enqueueOutput\(packet\)/);
  assert.match(audio, /Thread\(::audioWriterLoop, "Retra-Audio"\)/);
  assert.match(audio, /Process\.THREAD_PRIORITY_AUDIO/);
  assert.match(audio, /AudioTrack\.WRITE_BLOCKING/);
  assert.match(audio, /PREBUFFER_MS = 32/);
  assert.match(audio, /safeUnderrunCount/);
  assert.match(audio, /MAX_PENDING_AUDIO_MS = 200/);
});

test('JNI framebuffer conversion avoids per-frame temporary array copies', () => {
  assert.match(native, /GetPrimitiveArrayCritical\(outputPixels/);
  assert.match(native, /ReleasePrimitiveArrayCritical\(outputPixels/);
  assert.match(native, /const mColor\* sourceRow/);
  assert.match(native, /jint\* destinationRow/);
});


test('does not double-apply mGBA frameskip in Kotlin presentation', () => {
  const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
  const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
  assert.match(settings, /setCoreConfigOption\("frameskip", frameSkip\.toString\(\)\)/);
  assert.doesNotMatch(session, /renderFrameCounter/);
  assert.doesNotMatch(session, /activeFrameSkip/);
});


test('frameskip UI explains that zero is the smooth setting', () => {
  const html = read('app/src/main/assets/retra/index.html');
  assert.match(html, /<strong>Frameskip<\/strong>/);
  assert.match(html, /0 = smoothest \(~60 FPS\); 1 = ~30 FPS/);
});
