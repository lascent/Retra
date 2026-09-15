const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('v1.0.3 exposes Edit name in the ROM detail overflow menu', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const js = read('app/src/main/assets/retra/categories-ui.js');
  assert.match(html, /id="menuEditNameBtn"[\s\S]*Edit name/);
  assert.match(html, /id="romRenameModal"/);
  assert.match(html, /id="romRenameInput"[^>]*maxlength="64"/);
  assert.match(js, /menuEditNameBtn\?\.addEventListener\('click', openRomRenameModal\)/);
  assert.match(js, /window\.AndroidBridge\.renameRomTitle\(romId, nextTitle\)/);
  assert.match(js, /rom\.title = nextTitle/);
  assert.match(js, /currentRomCard\.dataset\.title = nextTitle/);
});

test('ROM rename is persisted natively without changing the ROM file name', () => {
  const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  const store = read('app/src/main/java/com/retra/emulator/RomIdentityStore.kt');
  assert.match(main, /fun renameRomTitle\(romId: String, requestedTitle: String\): Boolean/);
  assert.match(main, /romIdentityStore\.updateDisplayName\(romId, normalized\)/);
  assert.match(main, /putString\(titleKey\(romId\), normalized\)/);
  assert.match(main, /syncCloudAsync\(showResult = false\)/);
  assert.match(store, /fun updateDisplayName\(romId: String, displayName: String\)/);
  assert.match(store, /UPDATE rom_records SET display_name = :displayName/);
  assert.doesNotMatch(main, /renameRomTitle[\s\S]{0,600}(fileNameKey|current_file_uri|launch_path)\(/);
});

test('Retra release metadata is v1.0.3', () => {
  const gradle = read('app/build.gradle.kts');
  const html = read('app/src/main/assets/retra/index.html');
  assert.match(gradle, /versionCode = 450/);
  assert.match(gradle, /versionName = "1\.0\.3"/);
  assert.match(html, /Retra v1\.0\.3/);
});
