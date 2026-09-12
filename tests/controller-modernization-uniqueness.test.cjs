const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('screen editor prevents duplicate control types and hides already-added rows', () => {
  const js = read('app/src/main/assets/retra/controller-editor-ui.js') + '\n' + read('app/src/main/assets/retra/screen-editor.js');
  assert.match(js, /function previewControlTypesConflict\(/);
  assert.match(js, /function isPreviewControlTypePlaced\(/);
  assert.match(js, /item\.hidden = alreadyAdded/);
  assert.match(js, /if \(isPreviewControlTypePlaced\(type\)\)/);
  assert.match(js, /same control\. Keep the first valid visible instance and drop the rest/);
});

test('built-in A\/B cluster owns A and B so individual duplicates are unavailable', () => {
  const js = read('app/src/main/assets/retra/controller-editor-ui.js') + '\n' + read('app/src/main/assets/retra/screen-editor.js');
  assert.match(js, /firstType === 'ab'.*secondType === 'buttonA'.*secondType === 'buttonB'/s);
  assert.match(js, /secondType === 'ab'.*firstType === 'buttonA'.*firstType === 'buttonB'/s);
});

test('classic controller visuals remain active while uniqueness logic stays separate', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const controls = read('app/src/main/assets/retra/style-controls.css');
  const editor = read('app/src/main/assets/retra/style-screen-editor.css');
  assert.equal(html.includes('style-controller-modern.css'), false);
  assert.ok(html.indexOf('ui-polish.css') > html.indexOf('style-features.css'));
  assert.match(editor, /\.preview-dpad \.up::before/);
  assert.match(editor, /\.preview-ab span/);
  assert.match(controls, /#screenSizePage \.myboy-menu-icon/);
});

test('native gameplay icons match the current Screen Editor visual set', () => {
  const fast = read('app/src/main/res/drawable/ic_controller_fast_forward.xml');
  const dpad = read('app/src/main/res/drawable/ic_controller_dpad_up.xml');
  const quickSave = read('app/src/main/res/drawable/ic_controller_quick_save.xml');
  const layout = read('app/src/main/res/layout/activity_main.xml');
  assert.match(fast, /M5\.5,6\.5 L11,12 L5\.5,17\.5/);
  assert.match(dpad, /M1,11\.5 L8,4\.5 L15,11\.5/);
  assert.match(quickSave, /M5\.5,4\.5 L16\.3,4\.5 L18\.5,6\.7/);
  assert.doesNotMatch(layout, /@drawable\/bg_controller_dpad_center/);
});
