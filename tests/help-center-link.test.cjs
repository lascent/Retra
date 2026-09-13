const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const settingsUi = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/settings-ui.js'), 'utf8');
const indexHtml = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');
const webUiController = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/WebUiController.kt'), 'utf8');

test('Help Center opens the Retra troubleshooting guide', () => {
  assert.match(indexHtml, /data-action="Open help center"[^>]*>Open help center<\/button>/);
  assert.match(settingsUi, /const RETRA_HELP_CENTER_URL = 'https:\/\/github\.com\/lascent\/Retra\/blob\/main\/TROUBLESHOOTING\.md';/);
  assert.match(settingsUi, /btn\.dataset\.action === 'Open help center'/);
  assert.match(settingsUi, /window\.location\.href = RETRA_HELP_CENTER_URL/);
});

test('external WebView navigation is delegated to Android ACTION_VIEW', () => {
  assert.match(webUiController, /shouldOverrideUrlLoading/);
  assert.match(webUiController, /Intent\(Intent\.ACTION_VIEW, uri\)/);
});
