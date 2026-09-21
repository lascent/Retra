const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('gameplay uses a triple-buffer latest-frame mailbox so presentation cannot block mGBA', () => {
  const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const mailbox = read('app/src/main/java/com/retra/emulator/GameplayFrameMailbox.kt');
  const multiplayer = read('app/src/main/java/com/retra/emulator/MultiplayerController.kt');
  assert.match(activity, /gameplayFrameMailbox = GameplayFrameMailbox\(\)/);
  assert.match(mailbox, /freeBuffers\.addLast\(IntArray\(pixelCount\)\)/);
  assert.match(mailbox, /pending\?\.let \{ stale/);
  assert.match(mailbox, /rendering\?\.let \{ previous/);
  assert.match(multiplayer, /shaderController\.requestPresentLatest\(\)/);
  assert.doesNotMatch(multiplayer, /val pixelsToPresent = synchronized\(frameLock\)/);
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
