const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const gameplay = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/GameplayController.kt'), 'utf8');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');

test('foreground gameplay silently Quick Saves before Home/background and keeps auto-resume', () => {
  assert.match(main, /override fun onUserLeaveHint\(\)[\s\S]*saveLifecycleQuickStateIfEnabled\(\)/);
  assert.match(main, /override fun onPause\(\)[\s\S]*saveLifecycleQuickStateIfEnabled\(\)[\s\S]*saveAutoStateIfEnabled\(\)[\s\S]*stopEmulation\(\)/);
  assert.match(gameplay, /saveLifecycleQuickStateIfEnabled\(\): Boolean[\s\S]*saveStateToSlot\(0, showNotice = false\)/);
});

test('Recents removal and orderly teardown retry Quick Save without duplicate writes', () => {
  assert.match(main, /override fun onStop\(\)[\s\S]*saveLifecycleQuickStateIfEnabled\(\)/);
  assert.match(main, /override fun onDestroy\(\)[\s\S]*saveLifecycleQuickStateIfEnabled\(\)[\s\S]*stopEmulation\(\)/);
  assert.match(main, /lifecycleQuickSaveCompletedForForeground = false/);
  assert.match(gameplay, /lifecycleQuickSaveCompletedForForeground\) return lifecycleQuickSaveCompletedForForeground/);
  assert.match(gameplay, /if \(saved\) lifecycleQuickSaveCompletedForForeground = true/);
});

test('automatic Quick Save respects Auto save & load and avoids paired-link corruption', () => {
  assert.match(gameplay, /prefs\.getBoolean\(MainActivity\.AUTO_SAVE_LOAD_PREF, true\)/);
  assert.match(gameplay, /localLinkActive \|\| remoteTransport\.isActive/);
  assert.match(html, /Quick-save on background\/exit and keep automatic resume available on next launch/);
});
