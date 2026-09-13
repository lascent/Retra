const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('Retra exposes version 1.0.2 consistently with an upgrade-safe versionCode', () => {
  const gradle = read('app/build.gradle.kts');
  const html = read('app/src/main/assets/retra/index.html');
  assert.match(gradle, /versionCode = 449/);
  assert.match(gradle, /versionName = "1\.0\.2"/);
  assert.match(html, /Retra v1\.0\.2/);
});

test('Privacy Policy is a real offline in-app page instead of a toast-only action', () => {
  const html = read('app/src/main/assets/retra/index.html');
  assert.match(html, /data-open-page="privacyPolicyPage"/);
  assert.match(html, /id="privacyPolicyPage"/);
  assert.match(html, /Local game data\./);
  assert.match(html, /Google Drive\./);
  assert.match(html, /No advertising or analytics SDK\./);
});

test('all Screen Editor action-combo controls are implemented in native gameplay', () => {
  const kt = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
  for (const type of ['comboAB','comboLR','comboLA','comboLB','comboRA','comboRB','turboAB','screenshot']) {
    assert.match(kt, new RegExp(`"${type}"`));
  }
  assert.match(kt, /bindMultiKeyControl\(it, intArrayOf\(KEY_A, KEY_B\)\)/);
  assert.match(kt, /bindTurboAbControl/);
  assert.match(kt, /saveGameplayScreenshot\(\)/);
  assert.match(kt, /alreadyApplied = mutableSetOf<String>\(\)/);
});

test('native screenshot extra control uses a dedicated controller drawable', () => {
  const drawable = read('app/src/main/res/drawable/ic_controller_screenshot.xml');
  assert.match(drawable, /android:viewportWidth="24"/);
  assert.match(drawable, /strokeColor="#FFF7F9FC"/);
});

test('native build is pinned for 16 KB page-size compatibility', () => {
  const gradle = read('app/build.gradle.kts');
  const cmake = read('app/src/main/cpp/CMakeLists.txt');
  assert.match(gradle, /ndkVersion = "28\.0\.13004108"/);
  assert.match(cmake, /max-page-size=16384/);
});
