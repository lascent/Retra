const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');

test('Android backup allowlists do not contain redundant file excludes', () => {
  const legacy = read('app/src/main/res/xml/backup_rules.xml');
  const modern = read('app/src/main/res/xml/data_extraction_rules.xml');
  for (const xml of [legacy, modern]) {
    assert.match(xml, /<include domain="file" path="persistent_data\/"\s*\/>/);
    assert.match(xml, /<include domain="file" path="datastore\/"\s*\/>/);
    assert.doesNotMatch(xml, /<exclude domain="file" path="library_content\/"\s*\/>/);
    assert.doesNotMatch(xml, /<exclude domain="file" path="save_work\/"\s*\/>/);
  }
});

test('Google Drive restore handler uses a parser-safe guarded listener', () => {
  const js = read('app/src/main/assets/retra/settings-ui.js');
  assert.match(js, /if \(restoreCloudBackupBtn\) \{/);
  assert.match(js, /restoreCloudBackupBtn\.addEventListener\('click', function \(\) \{/);
  assert.match(js, /window\.AndroidBridge\.restoreFromGoogleDrive\(\)/);
});
