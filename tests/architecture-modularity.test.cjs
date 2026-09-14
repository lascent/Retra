const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { JS_MODULES, CSS_MODULES } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const exists = rel => fs.existsSync(path.join(root, rel));
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const html = read('app/src/main/assets/retra/index.html');

test('MainActivity is below 3k lines and delegates major responsibilities', () => {
  const lines = main.split(/\r?\n/).length;
  assert.ok(lines < 3000, `MainActivity is ${lines} lines; expected < 3000`);
  for (const component of [
    'RetraFileOps',
    'GameplayLayoutRepository',
    'RomAssetRepository',
    'SaveDataRepository',
    'SaveStateRepository',
    'CheatRepository',
    'SaveTransferRepository',
    'StatisticsRepository',
    'WebUiController',
    'RemoteLinkTransport',
    'ShaderRepository',
    'ShaderController',
    'GoogleDriveApiRepository',
    'CloudSyncCoordinator',
    'EmulationSpeedPolicy',
  ]) {
    assert.ok(exists(`app/src/main/java/com/retra/emulator/${component}.kt`), `${component}.kt missing`);
    assert.match(main, new RegExp(component));
  }
});

test('activity-scale subsystems are split into focused controller modules', () => {
  for (const component of [
    'AudioController',
    'GameplayController',
    'GameplayLayoutController',
    'GameplayTouchController',
    'MultiplayerController',
    'SettingsController',
    'RomUiController',
    'EmulationSessionManager',
    'RomPersistenceController',
  ]) {
    const rel = `app/src/main/java/com/retra/emulator/${component}.kt`;
    assert.ok(exists(rel), `${component}.kt missing`);
    const lines = read(rel).split(/\r?\n/).length;
    assert.ok(lines <= 1200, `${component}.kt is ${lines} lines; expected <= 1200`);
  }
  assert.doesNotMatch(main, /fun renderLinkRemoteScreen\(/);
  assert.doesNotMatch(main, /fun renderCheatsScreen\(/);
  assert.doesNotMatch(main, /fun bindControls\(/);
  assert.doesNotMatch(main, /fun updateSetting\(/);
  assert.doesNotMatch(main, /fun importUri\(/);
  assert.doesNotMatch(main, /fun startEmulation\(/);
  assert.doesNotMatch(main, /fun migrateLegacyRomIdentityIfNeeded\(/);
});

test('Web UI JavaScript is feature-split and loaded in deterministic order', () => {
  assert.equal(exists('app/src/main/assets/retra/script.js'), false);
  let cursor = -1;
  for (const file of JS_MODULES) {
    const source = read(`app/src/main/assets/retra/${file}`);
    assert.ok(source.split(/\r?\n/).length <= 1800, `${file} is too large`);
    const index = html.indexOf(`<script src="${file}"></script>`);
    assert.ok(index > cursor, `${file} is missing or out of order`);
    cursor = index;
  }
});

test('Web UI CSS is cascade-preserving and split into bounded feature files', () => {
  assert.equal(exists('app/src/main/assets/retra/style.css'), false);
  let cursor = -1;
  for (const file of CSS_MODULES) {
    const source = read(`app/src/main/assets/retra/${file}`);
    assert.ok(source.split(/\r?\n/).length <= 2500, `${file} is too large`);
    const index = html.indexOf(`href="${file}"`);
    assert.ok(index > cursor, `${file} is missing or out of order`);
    cursor = index;
  }
  assert.ok(html.indexOf('href="ui-polish.css"') > cursor, 'ui-polish.css must remain last for override order');
});

test('filesystem, WebView and Remote Link policy no longer live as MainActivity implementations', () => {
  const fileOps = read('app/src/main/java/com/retra/emulator/RetraFileOps.kt');
  const web = read('app/src/main/java/com/retra/emulator/WebUiController.kt');
  const remote = read('app/src/main/java/com/retra/emulator/RemoteLinkTransport.kt');
  assert.match(fileOps, /fun atomicCopyVerified/);
  assert.match(fileOps, /fun atomicWriteBytes/);
  assert.match(web, /WebViewAssetLoader/);
  assert.match(web, /WebUiPerformanceTuner\.apply/);
  assert.match(remote, /PACKET_RESYNC_STATE/);
  assert.match(remote, /inputDelayFrames/);
  assert.doesNotMatch(main, /private fun writeRemotePacket/);
  assert.doesNotMatch(main, /private fun atomicCopyVerified/);
});
