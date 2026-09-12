const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const native = fs.readFileSync(path.join(__dirname, '..', 'app', 'src', 'main', 'cpp', 'native-lib.cpp'), 'utf8');

test('Automatic preserves mGBA cartridge and Pokemon ROM-hack save detection', () => {
  assert.match(native, /value\.empty\(\) \|\| value == "automatic"\) return false/);
  assert.doesNotMatch(native, /value == "automatic"\) \*outType = GBA_SAVEDATA_AUTODETECT/);
});

test('Manual Flash 128K remains mapped to mGBA FLASH1M', () => {
  assert.match(native, /flash 128k[^\n]*GBA_SAVEDATA_FLASH1M/);
});

test('Automatic has a conservative Pokemon FLASH1M safety fallback', () => {
  assert.match(native, /looksLikePokemonFlash1MRom/);
  assert.match(native, /"FLASH1M_V"/);
  assert.match(native, /pokemonGameCode/);
  assert.match(native, /GBASavedataForceType\(&gba->memory\.savedata, GBA_SAVEDATA_FLASH1M\)/);
});

test('normal Reset reapplies the safe save-type handler', () => {
  const marker = 'Java_com_retra_emulator_MainActivity_resetCore';
  const start = native.indexOf(marker);
  assert.ok(start >= 0, 'resetCore JNI function missing');
  const body = native.slice(start, native.indexOf('Java_com_retra_emulator_MainActivity_clearNativeCheats', start));
  assert.match(body, /core->reset\(core\);[\s\S]*applyGbaSaveTypeOverride\(core\);/);
});
