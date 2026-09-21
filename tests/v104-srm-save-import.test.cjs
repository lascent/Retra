const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('gameplay Import save accepts both .sav and .srm battery saves', () => {
  const gameplay = read('app/src/main/java/com/retra/emulator/GameplayEnhancementController.kt');
  const transfer = read('app/src/main/java/com/retra/emulator/SaveTransferRepository.kt');
  const flow = read('app/src/main/java/com/retra/emulator/SaveImportController.kt');

  assert.match(gameplay, /"\.sav \/ \.srm for \$currentRomTitle"/);
  assert.match(transfer, /importExt !in setOf\("sav", "srm"\)/);
  assert.match(transfer, /Choose a \.sav or \.srm battery save file/);
  assert.match(transfer, /normalizedSaveBase\(displayName\)/);
  assert.match(transfer, /existing\.length\(\) != importedSize/);
  assert.match(transfer, /replaceBatterySaveFromFile\(target\.id, temp\)/);
  assert.match(flow, /commitActiveWorkingSaves\(\)/);
  assert.match(flow, /saveStates\.autoFile\(session\.romId\)\.delete\(\)/);
});

test('.srm remains normalized to canonical per-ROM .sav storage', () => {
  const transfer = read('app/src/main/java/com/retra/emulator/SaveTransferRepository.kt');
  const saveData = read('app/src/main/java/com/retra/emulator/SaveDataRepository.kt');

  assert.match(transfer, /val temp = File\(tempDir, "\$\{fileOps\.sanitizeFileName\(target\.id\)\}_\$\{System\.nanoTime\(\)\}\.sav"\)/);
  assert.match(transfer, /listOf\("\.sav", "\.srm", "\.rtc"/);
  assert.match(saveData, /"game\.sav"/);
});
