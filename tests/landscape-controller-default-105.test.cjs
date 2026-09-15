const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('landscape defaults use 105% for D-pad A/B and L/R while menu and Start/Select remain 100%', () => {
  const editor = read('app/src/main/assets/retra/controller-editor-ui.js') + '\n' + read('app/src/main/assets/retra/screen-editor.js');
  const native = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');

  assert.match(editor, /DEFAULT_CONTROLLER_SCALE_LANDSCAPE_PRIMARY = 1\.05/);
  assert.match(editor, /landscapePrimaryBaseControls = new Set\(\['shoulderLeft', 'shoulderRight', 'dpad', 'ab'\]\)/);
  assert.match(editor, /function defaultBaseControllerScale\(controlId,[\s\S]*?orientation === 'landscape'[\s\S]*?landscapePrimaryBaseControls\.has\(controlId\)[\s\S]*?DEFAULT_CONTROLLER_SCALE_LANDSCAPE_PRIMARY/);
  assert.match(editor, /case 'menu':[\s\S]*?left = \(width - scaledWidth\) \/ 2;[\s\S]*?top = menuTop;/);

  assert.match(native, /val landscapePrimaryScale = 1\.05f/);
  assert.match(native, /view in listOf\(binding\.buttonL, binding\.buttonR, binding\.dpadContainer, binding\.abContainer\)/);
  assert.match(native, /val menuTop = maxOf\(dp\(6f\), parentHeight \* 0\.014f\)/);
  assert.match(native, /binding\.utilityBar,[\s\S]*?\(parentWidth - width\(binding\.utilityBar\)\) \/ 2f,[\s\S]*?menuTop/);
});

test('removed base controls are synchronized into the active layout profile immediately', () => {
  const editor = read('app/src/main/assets/retra/screen-editor.js');
  const profiles = read('app/src/main/assets/retra/layout-profiles.js');

  assert.match(editor, /state\.hidden = true/);
  assert.match(editor, /syncActiveLayoutProfileControllerFromEditor\(orientation, payload\)/);
  assert.match(profiles, /function syncActiveLayoutProfileControllerFromEditor\(orientation, controllerPayload\)/);
  assert.match(profiles, /profile\.layouts\[orientation\] = \{[\s\S]*?controller,[\s\S]*?screen: deepCloneLayoutValue\(existing\.screen\)/);
});
