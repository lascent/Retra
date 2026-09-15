const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const session = read('app/src/main/java/com/retra/emulator/EmulationSessionManager.kt');
const html = read('app/src/main/assets/retra/index.html');
const settings = read('app/src/main/assets/retra/settings-ui.js');
const shaderRepo = read('app/src/main/java/com/retra/emulator/ShaderRepository.kt');
const shaderView = read('app/src/main/java/com/retra/emulator/ShaderGameView.kt');
const shaderController = read('app/src/main/java/com/retra/emulator/ShaderController.kt');
const drive = read('app/src/main/java/com/retra/emulator/GoogleDriveApiRepository.kt');
const cloud = read('app/src/main/java/com/retra/emulator/CloudSyncCoordinator.kt');
const transfer = read('app/src/main/java/com/retra/emulator/SaveTransferRepository.kt');
const speed = read('app/src/main/java/com/retra/emulator/EmulationSpeedPolicy.kt');

test('custom GLSL shaders install, validate, compile and render only on demand', () => {
  assert.match(shaderRepo, /ALLOWED_EXTENSIONS = setOf\("glsl", "frag", "fs", "fsh"\)/);
  assert.match(shaderRepo, /MAX_SHADER_BYTES = 128 \* 1024/);
  assert.match(shaderRepo, /void main/);
  assert.match(shaderRepo, /gl_fragcolor/);
  assert.match(shaderRepo, /utexture/);
  assert.match(shaderView, /setEGLContextClientVersion\(2\)/);
  assert.match(shaderView, /RENDERMODE_WHEN_DIRTY/);
  assert.match(shaderView, /fun setFragmentShader/);
  assert.match(shaderView, /GLUtils\.texSubImage2D/);
  assert.match(shaderController, /normalView\.visibility = View\.VISIBLE/);
  assert.match(shaderController, /shaderView\.visibility = View\.VISIBLE/);
  assert.match(html, /id="glslShaderBtn"/);
  assert.match(settings, /AndroidBridge\.installGlslShader/);
  assert.match(settings, /AndroidBridge\.setGlslShader/);
});

test('Google Drive backup uses OAuth Drive API snapshots while SAF remains local-only', () => {
  assert.match(cloud, /https:\/\/www\.googleapis\.com\/auth\/drive\.file/);
  assert.match(cloud, /MODE_API = "api"/);
  assert.match(cloud, /Identity\.getAuthorizationClient\(activity\)/);
  assert.doesNotMatch(cloud, /MODE_SAF|onFolderFallbackRequested/);
  assert.match(drive, /https:\/\/www\.googleapis\.com\/drive\/v3/);
  assert.match(drive, /https:\/\/www\.googleapis\.com\/upload\/drive\/v3/);
  assert.match(drive, /Retra Backups/);
  assert.match(drive, /retraSha256/);
  assert.match(drive, /md5Checksum/);
  assert.match(drive, /fun sha256/);
  assert.match(transfer, /fun syncDetailed/); // retained for explicit local export/import paths only
  assert.match(transfer, /copyFileToDocumentVerified/);
  assert.doesNotMatch(settings, /Drive folder/);
});

test('slow motion and turbo share one exact supported speed policy', () => {
  assert.match(speed, /doubleArrayOf\(0\.2, 0\.5, 1\.0, 2\.0, 4\.0, 8\.0, 16\.0\)/);
  assert.match(html, /data-fastforward-value="0\.2x"/);
  assert.match(html, /data-fastforward-value="0\.5x"/);
  assert.match(html, /data-fastforward-value="1x"/);
  assert.match(html, /data-fastforward-value="16x"/);
  assert.match(session, /val speed = EmulationSpeedPolicy\.sanitize\(activeEmulationSpeed\)/);
  assert.match(session, /TurboFramePolicy\.framesPerSlice\(speed, FRAME_TIME_NS\)/);
  assert.match(session, /TurboFramePolicy\.sliceCadenceNs\(/);
  assert.match(main, /removeSuffix\("x"\)\.toDoubleOrNull\(\)/);
  assert.match(main, /putString\(EMULATION_SPEED_PREF, preferredEmulationSpeed\.toString\(\)\)/);
});
