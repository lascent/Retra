const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/MainActivity.kt'), 'utf8');
const shader = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/ShaderRepository.kt'), 'utf8');

test('v4.45 cloud/save graph uses explicit types to avoid recursive Kotlin inference', () => {
  assert.match(main, /(?:private|internal) val saveStates: SaveStateRepository by lazy/);
  assert.match(main, /(?:private|internal) val saveTransfer: SaveTransferRepository by lazy/);
  assert.match(main, /(?:private|internal) val driveApi: GoogleDriveApiRepository by lazy/);
  assert.match(main, /(?:private|internal) val cloudSync: CloudSyncCoordinator by lazy/);
  assert.match(main, /(?:private|internal) val driveAuthRecoveryLauncher: ActivityResultLauncher<Intent>/);
  assert.doesNotMatch(main, /(?:private|internal) val cloudSync by lazy/);
});

test('ShaderRepository sourceFor uses a Kotlin-safe block body for built-in and custom shaders', () => {
  const start = shader.indexOf('fun sourceFor(id: String): String? {');
  const end = shader.indexOf('fun optionFor', start);
  const block = shader.slice(start, end);
  assert.ok(start >= 0 && end > start);
  assert.match(block, /val builtIn = BUILT_IN_BY_ID\[id\]/);
  assert.match(block, /if \(builtIn != null\) return builtIn\.source/);
  assert.match(block, /if \(!id\.startsWith\("custom:"\)\) return null/);
  assert.doesNotMatch(block, /String\?\s*=\s*when/);
});
