const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const repo = read('app/src/main/java/com/retra/emulator/SaveStateRepository.kt');
const main = read('app/src/main/java/com/retra/emulator/MainActivity.kt');
const gameplay = read('app/src/main/java/com/retra/emulator/GameplayController.kt');
const js = read('app/src/main/assets/retra/library-ui.js');
const html = read('app/src/main/assets/retra/index.html');

test('normal save states persist custom labels in metadata while Quick is immutable', () => {
  assert.match(repo, /fun rename\(romId: String, slot: Int, requestedLabel: String\): Boolean/);
  assert.match(repo, /slot !in 1\.\.10/);
  assert.match(repo, /fun displayLabel\(slot: Int, romId: String\): String/);
  assert.match(repo, /if \(slot == 0\) return "Quick"/);
  assert.match(repo, /\.put\("label", normalized\)/);
  assert.match(repo, /preservedLabel/);
  assert.match(repo, /\.put\("label", displayLabel\(slot, romId\)\)/);
});

test('WebView Recent saves can rename normal slots and never offers rename for Quick', () => {
  assert.match(html, /id="recentSaveActionsModal"/);
  assert.match(html, /id="recentSaveRenameModal"/);
  assert.match(js, /recentSaveRenameAction\.hidden = slot === 0/);
  assert.match(js, /Quick Save cannot be renamed/);
  assert.match(js, /AndroidBridge\.renameRomSaveState\(romId, slot, name\)/);
  assert.match(main, /fun renameRomSaveState\(romId: String, slot: Int, label: String\): Boolean/);
});

test('gameplay Save state and Load state menus use renamed labels', () => {
  assert.match(gameplay, /text = saveStates\.displayLabel\(slot, currentRomId\)/);
  assert.doesNotMatch(gameplay, /saveStates\.slotLabel\(slot\)/);
});

test('long press opens save-state actions instead of immediately deleting', () => {
  assert.match(js, /openRecentSaveActions\(card, save\)/);
  assert.match(js, /recentSaveDeleteAction\?\.addEventListener\('click'/);
});
