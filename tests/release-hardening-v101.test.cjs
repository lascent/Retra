const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const root = path.join(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');

test('portable settings keep all BIOS-dependent state device-local', () => {
  const prefs = read('app/src/main/java/com/retra/emulator/RetraPreferences.kt');
  for (const key of [
    'bios_gba_path_v1', 'bios_gb_path_v1', 'bios_gbc_path_v1',
    'use_bios_v1', 'boot_bios_v1', 'bios_last_label_v1'
  ]) {
    assert.match(prefs, new RegExp(`"${key}"`));
  }
  const excluded = prefs.match(/PORTABLE_EXCLUDED_KEYS = setOf\(([\s\S]*?)\n\s*\)/)?.[1] || '';
  assert.match(excluded, /"use_bios_v1"/);
  assert.match(excluded, /"boot_bios_v1"/);
  assert.match(excluded, /"bios_last_label_v1"/);
});

test('high-frequency sliders use a rate-limited specialized native bridge', () => {
  const js = read('app/src/main/assets/retra/settings-ui.js');
  const settings = read('app/src/main/java/com/retra/emulator/SettingsController.kt');
  const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  assert.match(js, /createNativeRangeDispatcher/);
  assert.match(js, /setRangeSetting/);
  assert.match(js, /createNativeRangeDispatcher\('volume', 48\)/);
  assert.match(js, /createNativeRangeDispatcher\('buttonsOpacity', 64\)/);
  assert.match(js, /createNativeRangeDispatcher\('frameSkip', 72\)/);
  assert.match(js, /createNativeRangeDispatcher\('smcCheck', 96\)/);
  assert.match(settings, /fun MainActivity\.updateRangeSetting/);
  assert.match(settings, /if \(commit\) syncAppFolderAsync/);
  assert.match(activity, /fun setRangeSetting\(key: String, value: Int, commit: Boolean\)/);
});

test('native build is locked to the documented exact mGBA revision', () => {
  const lock = read('third_party/mgba.lock');
  const cmake = read('app/src/main/cpp/CMakeLists.txt');
  const sha = '543a197582c30364584d773a974d7f991892fa43';
  assert.match(lock, new RegExp(`revision=${sha}`));
  assert.match(cmake, new RegExp(`RETRA_MGBA_EXPECTED_REVISION "${sha}"`));
  assert.match(cmake, /Wrong mGBA revision/);
  assert.match(cmake, /tracked local modifications/);
  assert.ok(fs.existsSync(path.join(root, 'tools/prepare_mgba.py')));
});

test('Android instrumentation suite covers preferences file durability and window policy', () => {
  const dir = path.join(root, 'app/src/androidTest/java/com/retra/emulator');
  const files = fs.readdirSync(dir).filter((name) => name.endsWith('InstrumentedTest.kt'));
  assert.ok(files.length >= 4, `expected >=4 instrumentation test classes, got ${files.length}`);
  assert.ok(files.includes('RetraPreferencesInstrumentedTest.kt'));
  assert.ok(files.includes('RetraFileOpsInstrumentedTest.kt'));
  assert.ok(files.includes('MainActivityWindowInstrumentedTest.kt'));
  assert.ok(files.includes('RomIdentityStoreInstrumentedTest.kt'));
  assert.ok(fs.existsSync(path.join(root, 'docs/REAL_DEVICE_VALIDATION_MATRIX_v1.0.1.md')));
});
