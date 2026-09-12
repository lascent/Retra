const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const romUi = fs.readFileSync('app/src/main/java/com/retra/emulator/RomUiController.kt', 'utf8');
const artwork = fs.readFileSync('app/src/main/java/com/retra/emulator/ArtworkRepository.kt', 'utf8');

test('ZIP imports preserve the inner playable ROM name as an artwork/title hint', () => {
  assert.match(romUi, /data class ExtractedArchivePayload[\s\S]*romEntryName:\s*String\?/);
  assert.match(romUi, /romEntryName\s*=\s*entryName/);
  assert.match(romUi, /archiveRomEntryName\s*=\s*extracted\.romEntryName/);
  assert.match(romUi, /val gameFileName\s*=\s*archiveRomEntryName[\s\S]*?:\s*displayName/);
  assert.match(romUi, /gameFileName\.substringBeforeLast\('\.'\)/);
  // Keep the actual source/archive filename for reconnect UX and file metadata.
  assert.match(romUi, /fileName\s*=\s*displayName/);
});

test('artwork lookup can recover inner ROM names from ZIPs imported by older Retra builds', () => {
  assert.match(artwork, /private fun readArchiveRomEntryTitle\(record: RomIdentityStore\.Record\)/);
  assert.match(artwork, /contentResolver\.openInputStream\(uri\)/);
  assert.match(artwork, /ZipInputStream\(BufferedInputStream\(raw\)\)/);
  assert.match(artwork, /ext in ARCHIVE_PLAYABLE_EXTENSIONS/);
  assert.match(artwork, /addSeed\(readArchiveRomEntryTitle\(record\)\)/);
  assert.match(artwork, /MAX_ARCHIVE_TITLE_ENTRIES\s*=\s*512/);
});

test('old cached artwork misses are retried after the title-normalization fix', () => {
  assert.match(artwork, /previousLookupVersion\s*=\s*meta\.optInt\("lookupVersion", 0\)/);
  assert.match(artwork, /previousLookupVersion\s*>=\s*ARTWORK_LOOKUP_VERSION/);
  assert.match(artwork, /meta\.put\("lookupVersion", ARTWORK_LOOKUP_VERSION\)/);
  assert.match(artwork, /ARTWORK_LOOKUP_VERSION\s*=\s*4/);
});
