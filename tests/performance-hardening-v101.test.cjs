const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('gameplay uses three framebuffer owners so Android presentation does not block mGBA', () => {
  const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const multiplayer = read('app/src/main/java/com/retra/emulator/MultiplayerController.kt');
  assert.match(activity, /presentationPixels = IntArray/);
  assert.match(multiplayer, /presentationPixels = IntArray\(videoWidth \* videoHeight\)/);
  assert.match(multiplayer, /val pixelsToPresent = synchronized\(frameLock\)/);
  assert.match(multiplayer, /displayPixels = presentationPixels/);
  assert.match(multiplayer, /presentationPixels = newest/);

  const lockBlock = multiplayer.match(/val pixelsToPresent = synchronized\(frameLock\) \{([\s\S]*?)\n\s*\}/)?.[1] || '';
  assert.doesNotMatch(lockBlock, /setPixels\(/);
  assert.doesNotMatch(lockBlock, /presentIfActive\(/);
  assert.match(multiplayer, /targetBitmap\.setPixels\(/);
});

test('range previews update runtime state without persisting DataStore until commit', () => {
  const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
  const persistBlock = settings.match(/if \(commit\) \{\n\s*when \(key\) \{([\s\S]*?)\n\s*\}\n\s*\}/)?.[1] || '';
  assert.match(persistBlock, /BUTTON_OPACITY_PREF/);
  assert.match(persistBlock, /FRAME_SKIP_PREF/);
  assert.match(persistBlock, /VOLUME_PREF/);
  assert.match(persistBlock, /SMC_CHECK_PREF/);
  assert.match(settings, /if \(commit\) syncAppFolderAsync\(showResult = false\)/);
});

test('serialized storage maintenance runs below live gameplay priority', () => {
  const executors = read('app/src/main/java/com/retra/emulator/RetraTaskExecutors.kt');
  assert.match(executors, /Process\.THREAD_PRIORITY_BACKGROUND/);
  assert.match(executors, /namedFactory\("Retra-Storage", Process\.THREAD_PRIORITY_BACKGROUND/);
  assert.match(executors, /Process\.setThreadPriority\(androidPriority\)/);
  // Link/network work deliberately stays at normal priority for multiplayer latency.
  assert.match(executors, /namedFactory\("Retra-Link"\)/);
});
