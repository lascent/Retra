const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const kotlin = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/GameplayLayoutController.kt'), 'utf8');
const editor = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/controller-editor-ui.js'), 'utf8') + '\n' +
  fs.readFileSync(path.join(root, 'app/src/main/assets/retra/screen-editor.js'), 'utf8');

test('separate A and B controls are applied to native gameplay', () => {
  assert.match(kotlin, /applySavedAbControls\(portrait\)/);
  assert.match(kotlin, /ensureSeparatedAbButtons\(\)/);
  assert.match(kotlin, /"buttonA" -> if \(buttonAState == null\)/);
  assert.match(kotlin, /"buttonB" -> if \(buttonBState == null\)/);
  assert.match(kotlin, /applySavedIndependentAbButton\(binding\.buttonA, buttonAState\)/);
  assert.match(kotlin, /applySavedIndependentAbButton\(binding\.buttonB, buttonBState\)/);
});

test('A/B grouped control and separate A or B cannot coexist in editor', () => {
  assert.match(editor, /firstType === 'ab'.*secondType === 'buttonA'.*secondType === 'buttonB'/s);
  assert.match(editor, /secondType === 'ab'.*firstType === 'buttonA'.*firstType === 'buttonB'/s);
  assert.match(editor, /if \(isPreviewControlTypePlaced\(type\)\)/);
  assert.match(editor, /item\.hidden = alreadyAdded/);
});

test('separated A/B follow controller opacity without double-fading grouped A/B', () => {
  assert.match(kotlin, /binding\.buttonA\.alpha = if \(binding\.buttonA\.parent === binding\.abContainer\) 1f else alpha/);
  assert.match(kotlin, /binding\.buttonB\.alpha = if \(binding\.buttonB\.parent === binding\.abContainer\) 1f else alpha/);
});
