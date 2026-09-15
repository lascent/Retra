const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('v1.0.3 is upgrade-safe and Android offers keep-data uninstall recovery', () => {
  const gradle = read('app/build.gradle.kts');
  const manifest = read('app/src/main/AndroidManifest.xml');
  const backupRules = read('app/src/main/res/xml/backup_rules.xml');
  const extractionRules = read('app/src/main/res/xml/data_extraction_rules.xml');
  assert.match(gradle, /versionCode = 450/);
  assert.match(gradle, /versionName = "1\.0\.3"/);
  assert.match(manifest, /android:allowBackup="true"/);
  assert.match(manifest, /android:hasFragileUserData="true"/);
  assert.match(manifest, /android:dataExtractionRules="@xml\/data_extraction_rules"/);
  assert.match(manifest, /android:fullBackupContent="@xml\/backup_rules"/);
  assert.match(backupRules, /<include domain="file" path="persistent_data\/" \/>/);
  assert.match(backupRules, /<include domain="file" path="datastore\/" \/>/);
  // Explicit include rules are an allowlist: unlisted ROM/working paths stay excluded
  // without invalid redundant <exclude> entries that Android Lint rejects.
  assert.doesNotMatch(backupRules, /<exclude domain="file" path="library_content\/" \/>/);
  assert.doesNotMatch(backupRules, /<exclude domain="file" path="save_work\/" \/>/);
  assert.match(extractionRules, /<cloud-backup>/);
  assert.match(extractionRules, /<device-transfer>/);
});

test('About exposes manual update checking and an in-app update prompt', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const js = read('app/src/main/assets/retra/settings-ui.js');
  assert.match(html, /data-check-updates/);
  assert.match(html, /id="appUpdateModal"/);
  assert.match(html, /Check for updates/);
  assert.match(js, /requestRetraUpdateCheck\(true\)/);
  assert.match(js, /window\.retraOnUpdateCheck/);
  assert.match(js, /No new updates available/);
  assert.match(js, /window\.setTimeout\(\(\) => requestRetraUpdateCheck\(false\), 1400\)/);
});

test('native updater is bounded, official-repository-only, and off the UI thread', () => {
  const updater = read('app/src/main/java/com/retra/emulator/AppUpdateController.kt');
  const activity = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
  assert.match(updater, /api\.github\.com\/repos\/lascent\/Retra\/releases\/latest/);
  assert.match(updater, /AUTO_CHECK_COOLDOWN_MS = 6L \* 60L \* 60L \* 1000L/);
  assert.match(updater, /connectTimeout = CONNECT_TIMEOUT_MS/);
  assert.match(updater, /readTimeout = READ_TIMEOUT_MS/);
  assert.match(updater, /networkExecutor\.execute/);
  assert.match(updater, /host != "github\.com"/);
  assert.match(activity, /fun checkForUpdates\(manual: Boolean\)/);
  assert.match(activity, /fun openUpdateUrl\(url: String\)/);
});

test('privacy disclosure covers automatic GitHub update checks without ROM/save upload', () => {
  const html = read('app/src/main/assets/retra/index.html');
  assert.match(html, /GitHub Releases update check/);
  assert.match(html, /do not include your ROMs, saves, or library contents/);
});
