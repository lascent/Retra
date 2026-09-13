const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repo = path.resolve(__dirname, '..');
const settings = fs.readFileSync(path.join(repo, 'app/src/main/java/com/retra/emulator/SettingsController.kt'), 'utf8');

function openFolderBody() {
  const start = settings.indexOf('internal fun MainActivity.openAppFolderInternal()');
  const end = settings.indexOf('internal fun MainActivity.deleteCloudPathsAsync', start);
  return settings.slice(start, end);
}

test('Open app folder avoids fragile resolveActivity preflight on OEM Android builds', () => {
  const body = openFolderBody();
  assert.doesNotMatch(body, /resolveActivity\(packageManager\)/);
  assert.match(body, /runCatching\s*\{[\s\S]*startActivity\(intent\)/);
});

test('Open app folder tries both provider root and root-document VIEW routes', () => {
  const body = openFolderBody();
  assert.match(body, /RetraDocumentsProvider\.rootUri\(\)/);
  assert.match(body, /DocumentsContract\.Root\.MIME_TYPE_ITEM/);
  assert.match(body, /RetraDocumentsProvider\.rootDocumentUri\(\)/);
  assert.match(body, /DocumentsContract\.Document\.MIME_TYPE_DIR/);
  assert.doesNotMatch(body.replace(/\/\/[^\n]*$/gm, ''), /ACTION_OPEN_DOCUMENT_TREE/);
});
