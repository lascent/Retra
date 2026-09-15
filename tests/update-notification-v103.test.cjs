const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('automatic update discovery is future-release safe on every fresh app process', () => {
  const updater = read('app/src/main/java/com/retra/emulator/AppUpdateController.kt');
  const ui = read('app/src/main/assets/retra/settings-ui.js');

  assert.match(updater, /releases\/latest/);
  assert.match(updater, /automaticCheckStartedThisProcess = AtomicBoolean\(false\)/);
  assert.match(updater, /!manual && !automaticCheckStartedThisProcess\.compareAndSet\(false, true\)/);
  assert.doesNotMatch(updater, /6L \* 60L \* 60L \* 1000L/);
  assert.doesNotMatch(updater, /update_last_checked_at_v1/);
  assert.match(updater, /compareVersions\(release\.versionName, installed\.name\) > 0/);
  assert.match(ui, /setTimeout\(\(\) => requestRetraUpdateCheck\(false\), 1400\)/);
  assert.match(ui, /requestRetraUpdateCheck\(true\)/);
});

test('version parsing remains numeric rather than lexical for future tags', () => {
  const updater = read('app/src/main/java/com/retra/emulator/AppUpdateController.kt');
  assert.match(updater, /core\.split\('\.'\)\.mapNotNull \{ it\.toIntOrNull\(\) \}/);
  assert.match(updater, /remotePart\.compareTo\(localPart\)/);
  assert.match(updater, /removePrefix\("v"\)\.removePrefix\("V"\)/);
});
