const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const display = read('app/src/main/java/com/retra/emulator/DisplayPerformanceManager.kt');
const gameplayPresenter = read('app/src/main/java/com/retra/emulator/GameplayFramePresenter.kt');
const webTuner = read('app/src/main/java/com/retra/emulator/WebUiPerformanceTuner.kt');
const webController = read('app/src/main/java/com/retra/emulator/WebUiController.kt');
const executors = read('app/src/main/java/com/retra/emulator/RetraTaskExecutors.kt');
const script = readWebJs(root);
const polish = read('app/src/main/assets/retra/ui-polish.css');

test('UI refresh policy explicitly targets up to 120 Hz without changing emulator cadence', () => {
  assert.match(display, /maxUiRefreshRateHz: Float = 120f/);
  assert.match(display, /preferredDisplayModeId/);
  assert.match(display, /preferredRefreshRate/);
  assert.match(display, /sameResolution/);
  assert.match(activity, /DisplayPerformanceManager\(this\)/);
  assert.match(display, /fun applyGameplayMode\(\)/);
  assert.match(display, /adaptiveRefreshCapHz/);
  assert.match(activity, /FRAME_TIME_NS = 16_742_706L/);
});

test('high-refresh policy is reapplied on resume and configuration changes', () => {
  assert.match(activity, /onResume\(\)[\s\S]*displayPerformanceManager\.applyPreferredMode\(\)/);
  assert.match(activity, /onConfigurationChanged[\s\S]*displayPerformanceManager\.reapplyAfterConfigurationChange\(\)/);
  assert.match(activity, /displayPerformanceManager\.restoreSystemDefault\(\)/);
});

test('WebView stays hardware accelerated and warm for responsive navigation', () => {
  assert.match(webTuner, /LAYER_TYPE_HARDWARE/);
  assert.match(webTuner, /offscreenPreRaster = true/);
  assert.match(webTuner, /RENDERER_PRIORITY_IMPORTANT/);
  assert.match(webController, /WebUiPerformanceTuner\.apply\(this\)/);
  assert.match(activity, /showWebUiWithoutBlankFrame/);
  assert.match(activity, /showEmulatorUiKeepingWebWarm/);
});

test('background execution ownership is centralized, serialized for storage and bounded for network work', () => {
  assert.match(executors, /newSingleThreadExecutor/);
  assert.match(executors, /ThreadPoolExecutor\(/);
  assert.match(executors, /LinkedBlockingQueue\(NETWORK_QUEUE_CAPACITY\)/);
  assert.match(executors, /NETWORK_MAX_THREADS = 4/);
  assert.match(executors, /CallerRunsPolicy/);
  assert.match(executors, /Retra-Storage/);
  assert.match(executors, /Retra-Link/);
  assert.doesNotMatch(executors, /newCachedThreadPool/);
  assert.match(activity, /(?:private|internal) val taskExecutors = RetraTaskExecutors\(\)/);
  assert.doesNotMatch(activity, /Executors\.newSingleThreadExecutor/);
  assert.doesNotMatch(activity, /Executors\.newCachedThreadPool/);
});

test('large libraries use paint containment without forcing every card into a GPU layer', () => {
  assert.match(script, /libraryGrid\.classList\.toggle\('large-library', libraryRoms\.length >= 36\)/);
  assert.match(polish, /\.library-grid\.large-library \.cover-card[\s\S]*content-visibility: auto/);
  assert.match(polish, /\.cover-card[\s\S]*contain: layout paint style/);
  assert.doesNotMatch(polish, /\.cover-card\s*\{[^}]*will-change:\s*transform/s);
});

test('touch scrolling avoids per-card favourite blur on phones', () => {
  assert.match(polish, /@media \(pointer: coarse\), \(max-width: 600px\)[\s\S]*\.favourite-heart[\s\S]*backdrop-filter: none/);
});


test('gameplay presentation is VSync-driven without changing native emulator timing', () => {
  assert.match(gameplayPresenter, /Choreographer\.FrameCallback/);
  assert.match(gameplayPresenter, /requestPresent\(\)/);
  assert.match(gameplayPresenter, /postFrameCallback\(this\)/);
  assert.match(session, /gameplayFramePresenter\.requestPresent\(framePublishResult\.generation\)/);
  assert.match(activity, /FRAME_TIME_NS = 16_742_706L/);
  assert.doesNotMatch(activity, /renderPending/);
});

test('mGBA exclusively owns frame-skip so presentation does not double-skip frames', () => {
  const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
  assert.match(settings, /setCoreConfigOption\("frameskip", frameSkip\.toString\(\)\)/);
  assert.doesNotMatch(activity, /activeFrameSkip/);
  assert.doesNotMatch(activity, /renderFrameCounter/);
  assert.doesNotMatch(session, /activeFrameSkip/);
  assert.doesNotMatch(session, /renderFrameCounter/);
  assert.match(session, /runTurboSlice\([\s\S]*discardAudio = false[\s\S]*\)/);
});

test('adaptive display policy protects low-end, battery-saver and thermal-constrained devices', () => {
  assert.match(display, /isLowRamDevice/);
  assert.match(display, /FOUR_GIB/);
  assert.match(display, /SIX_GIB/);
  assert.match(display, /isPowerSaveMode/);
  assert.match(display, /THERMAL_STATUS_SEVERE/);
  assert.match(display, /THERMAL_STATUS_MODERATE/);
  assert.match(display, /60f/);
  assert.match(display, /90f/);
  assert.match(display, /120f/);
});

test('retired playtime helpers are owned by StatisticsRepository after refactor', () => {
  const stats = read('app/src/main/java/com/retra/emulator/StatisticsRepository.kt');
  assert.match(stats, /fun storedPlaytimeMs\(romId: String\)/);
  assert.match(stats, /fun clearPlaytime\(romId: String\)/);
  assert.doesNotMatch(activity, /playtimeKey\(/);
});
