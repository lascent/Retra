const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const repo = read('app/src/main/java/com/retra/emulator/ArtworkRepository.kt');
const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const romAssets = read('app/src/main/java/com/retra/emulator/RomAssetRepository.kt');
const script = readWebJs(root);
const css = readWebCss(root);
const html = read('app/src/main/assets/retra/index.html');
const gradle = read('app/build.gradle.kts');

test('automatic artwork uses one low-priority native worker and pauses for gameplay', () => {
  assert.match(repo, /newSingleThreadExecutor/);
  assert.match(repo, /Thread\.MIN_PRIORITY/);
  assert.match(repo, /isGameplayActive\(\)/);
  assert.match(repo, /deferred \+= id/);
  assert.match(activity, /artworkRepository\.resumeDeferred\(\)/);
});

test('artwork downloads are bounded, optimized and cached per romId', () => {
  assert.match(repo, /MAX_DOWNLOAD_BYTES = 6L \* 1024L \* 1024L/);
  assert.match(repo, /COVER_MAX_WIDTH = 640/);
  assert.match(repo, /BACKGROUND_WIDTH = 960/);
  assert.match(repo, /Bitmap\.CompressFormat\.WEBP/);
  assert.match(repo, /persistent_data\/Metadata/);
  assert.match(repo, /artwork\.json/);
  assert.match(repo, /RETRY_INTERVAL_MS = 7L \* 24L/);
});

test('library uses viewport-lazy artwork requests and deterministic fallback colors', () => {
  assert.match(script, /IntersectionObserver/);
  assert.match(script, /queueRomArtwork/);
  assert.match(script, /rootMargin: '360px 0px'/);
  assert.match(script, /fallbackArtworkPalette/);
  assert.match(css, /--cover-fallback-a/);
  assert.doesNotMatch(script, /fetch\(url, \{ method: 'GET', cache: 'force-cache' \}\)/);
});

test('manual artwork remains protected and user controls are exposed', () => {
  assert.match(repo, /coverSource", "manual"/);
  assert.match(repo, /backgroundSource", "manual"/);
  assert.match(repo, /fun beginManual\(/);
  assert.match(romAssets, /beginManual\(romId, kind\)/);
  assert.match(romAssets, /markManual\(romId, kind\)/);
  assert.match(html, /id="automaticArtworkToggle"/);
  assert.match(html, /id="artworkWifiOnlyToggle"/);
  assert.match(html, /id="retryArtworkBtn"/);
});

test('Retra v1.0.1 release metadata is applied', () => {
  assert.match(gradle, /versionCode = 448/);
  assert.match(gradle, /versionName = "1\.0\.1"/);
});
