const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const artwork = fs.readFileSync('app/src/main/java/com/retra/emulator/ArtworkRepository.kt', 'utf8');

test('artwork lookup accepts both direct GBA filenames and ZIP inner-ROM names', () => {
  assert.match(artwork, /addSeed\(record\.fileName\)/);
  assert.match(artwork, /addSeed\(readArchiveRomEntryTitle\(record\)\)/);
  assert.match(artwork, /gba\|gbc\|gb\|mgba\|zip/);
});

test('GoodTools region and dump tags normalize to Libretro No-Intro-style names', () => {
  assert.match(artwork, /private fun canonicalRegionFor\(value: String\)/);
  assert.match(artwork, /U\|USA/);
  assert.match(artwork, /-> "USA"/);
  assert.match(artwork, /private fun stripReleaseMetadata\(value: String\)/);
  assert.match(artwork, /squareTag/);
  assert.ok(artwork.includes('commonly end in combinations such as \"(U) [!]\".'));
});

test('artwork lookup can repair omitted title separators without fuzzy matching', () => {
  assert.match(artwork, /listOf\(2, 1, 3, 4\)/);
  assert.match(artwork, /" - " \+ words\.drop\(split\)/);
  assert.match(artwork, /MAX_TITLE_CANDIDATES = 48/);
});

test('artwork lookup version is bumped so old NOT_FOUND results retry', () => {
  assert.match(artwork, /ARTWORK_LOOKUP_VERSION = 4/);
});


test('LeafGreen v1.1 scene names map to No-Intro LeafGreen Rev 1 artwork', () => {
  assert.match(artwork, /Leaf Green/);
  assert.match(artwork, /LeafGreen/);
  assert.match(artwork, /legacyRevisionFor/);
  assert.match(artwork, /USA, Europe/);
  assert.match(artwork, /Rev \$revision/);
});

test('European scene group names map to multilingual Super Mario Advance 4 artwork', () => {
  assert.match(artwork, /stripSceneGroupAfterRegion/);
  assert.match(artwork, /Menace/);
  assert.match(artwork, /Bros\. /);
  assert.match(artwork, /Europe\) \(En,Fr,De,Es,It\)/);
});

test('explicit ROM-hack base-game hints provide a safe last-resort cover candidate', () => {
  assert.match(artwork, /hackBaseArtworkCandidates/);
  assert.match(artwork, /Pokemon - FireRed Version \(USA\)/);
  assert.match(artwork, /Pokemon - LeafGreen Version \(USA\)/);
});
