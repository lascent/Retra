const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (rel) => fs.readFileSync(path.join(root, rel), 'utf8');

test('screen orientation UI no longer shows the misleading may-not-be-honored warning', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const js = read('app/src/main/assets/retra/settings-ui.js');
  assert.equal(html.includes('May not be honored by the system'), false);
  assert.equal(js.includes('May not be honored by the system'), false);
});

test('Auto rotate uses Android sensor orientation and unknown values fail safe', () => {
  const src = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');
  assert.match(src, /"Auto rotate"\s*->\s*ActivityInfo\.SCREEN_ORIENTATION_SENSOR/);
  assert.match(src, /"System default"\s*->\s*ActivityInfo\.SCREEN_ORIENTATION_UNSPECIFIED/);
  assert.match(src, /else\s*->\s*ActivityInfo\.SCREEN_ORIENTATION_UNSPECIFIED/);
});

test('orientation changes apply immediately and persisted choice is restored at startup', () => {
  const src = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  assert.match(src, /runOnUiThread\s*\{\s*applyPreferredOrientation\(value\)\s*\}/);
  assert.match(src, /preferredOrientationValue\s*=\s*prefs\.getString\(ORIENTATION_PREF,\s*"Auto rotate"\)/);
  assert.match(src, /applyPreferredOrientation\(preferredOrientationValue\)/);
});
