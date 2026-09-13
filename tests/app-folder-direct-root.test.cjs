const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repo = path.resolve(__dirname, '..');
const settings = fs.readFileSync(path.join(repo, 'app/src/main/java/com/retra/emulator/SettingsController.kt'), 'utf8');
const provider = fs.readFileSync(path.join(repo, 'app/src/main/java/com/retra/emulator/RetraDocumentsProvider.kt'), 'utf8');

test('Open app folder targets the Retra provider root, not a generic directory document', () => {
  const start = settings.indexOf('internal fun MainActivity.openAppFolderInternal()');
  assert.notEqual(start, -1);
  const body = settings.slice(start, settings.indexOf('\n}\n', start) + 2);
  assert.match(body, /RetraDocumentsProvider\.rootUri\(\)/);
  assert.match(body, /DocumentsContract\.Root\.MIME_TYPE_ITEM/);
  assert.doesNotMatch(body, /rootDocumentUri\(\)/);
  assert.doesNotMatch(body, /Document\.MIME_TYPE_DIR/);
  const codeOnly = body.replace(/\/\/[^\n]*$/gm, '');
  assert.doesNotMatch(codeOnly, /ACTION_OPEN_DOCUMENT_TREE/);
});

test('Retra root summary shows dynamic phone free space', () => {
  assert.match(provider, /Root\.COLUMN_SUMMARY -> row\.add\(rootSummary\(root\)\)/);
  assert.match(provider, /Retra storage • \$\{formatFreeSpace\(freeBytes\)\} free/);
  assert.match(provider, /StatFs\(root\.path\)\.availableBytes/);
  assert.match(provider, /bytes\.toDouble\(\) \/ 1_000_000_000\.0/);
  assert.match(provider, /kotlin\.math\.round\(decimalGb\)\.toLong\(\)/);
  assert.doesNotMatch(provider, /1024\.0 \* 1024\.0 \* 1024\.0/);
  assert.match(provider, /fun rootUri\(\) = DocumentsContract\.buildRootUri\(AUTHORITY, ROOT_ID\)/);
});
