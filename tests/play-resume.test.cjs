const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');

const root = path.resolve(__dirname, '..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const gameplay = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/GameplayController.kt'), 'utf8');
const session = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/EmulationSessionManager.kt'), 'utf8');
const script = readWebJs(root);

test('ROM detail action shows Play until an auto-resume state exists', () => {
  assert.match(script, /label\.textContent = canResume \? 'Resume' : 'Play'/);
  assert.match(script, /AndroidBridge\.hasResumeState/);
});

test('back/close always writes a dedicated resume state without overwriting Quick Save', () => {
  assert.match(session, /if \(romLoaded\) saveAutoState\(force = true\)/);
  assert.match(session, /Manual Quick Save \(slot 0\) stays untouched/);
});

test('explicit Resume force-loads the close/back auto state', () => {
  assert.match(main, /fun resumeRom\(/);
  assert.match(main, /pendingForceAutoResumeRomId = resolvedId/);
  assert.match(session, /loadAutoState\(force = true\)/);
});

test('closing a game refreshes the Library detail action immediately', () => {
  assert.match(session, /window\.retraNativeGameClosed/);
  assert.match(script, /window\.retraNativeGameClosed = function/);
});


test('gameplay Menu Close runs the full save-and-close pipeline', () => {
  assert.match(gameplay, /addMenuAction\(content, \"Close\"\) \{ closeEmulator\(\) \}/);
  assert.match(session, /if \(romLoaded\) saveAutoState\(force = true\)/);
  assert.match(session, /shutdownCore\(\)[\s\S]*commitActiveWorkingSaves\(\)/);
});

test('successful ROM load does not show the old in-game notification banner', () => {
  assert.doesNotMatch(session, /\$system ROM loaded|\$system patch loaded/);
  assert.match(session, /window\.retraNativeRomStarted/);
  const start = script.indexOf('window.retraNativeRomStarted = function');
  const end = script.indexOf('window.retraNativeSaveStatesChanged', start);
  const handler = script.slice(start, end);
  assert.doesNotMatch(handler, /showToast\(/);
});

