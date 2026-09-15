const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const html = fs.readFileSync(path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'retra', 'index.html'), 'utf8');

test('Google Drive restore lives inside Google Drive section directly below Backup Now', () => {
  const backupSection = html.indexOf('<div class="storage-section-label">Backup and restore</div>');
  const driveSection = html.indexOf('<div class="storage-section-label">Google Drive</div>');
  const backupNow = html.indexOf('id="cloudBackupNowBtn"');
  const restoreDrive = html.indexOf('id="restoreCloudBackupBtn"');
  const driveSettings = html.indexOf('id="syncSettingsBtn"');
  assert.ok(backupSection >= 0 && driveSection > backupSection);
  assert.ok(restoreDrive > driveSection, 'Drive restore must be in Google Drive section');
  assert.ok(backupNow > driveSection && backupNow < restoreDrive, 'Backup Now must precede Restore');
  assert.ok(driveSettings > restoreDrive, 'Google Drive settings must follow Restore');
  const localBackupMarkup = html.slice(backupSection, driveSection);
  assert.doesNotMatch(localBackupMarkup, /restoreCloudBackupBtn|Restore from Google Drive/);
});
