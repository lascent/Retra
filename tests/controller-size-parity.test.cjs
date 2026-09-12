const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('screen editor base sizes match native gameplay controls at 100 percent scale', () => {
  const css = read('app/src/main/assets/retra/style-layout-polish.css');
  const xml = read('app/src/main/res/layout/activity_main.xml');
  const kt = read('app/src/main/java/com/retra/emulator/GameplayLayoutController.kt');

  assert.match(css, /#screenSizePage \.preview-shoulder\{[\s\S]*?width:72px !important;[\s\S]*?height:30px !important;/);
  assert.match(xml, /@\+id\/buttonL[\s\S]*?layout_width="72dp"[\s\S]*?layout_height="30dp"/);

  assert.match(css, /#screenSizePage \.preview-dpad\{[\s\S]*?width:150px !important;[\s\S]*?height:150px !important;/);
  assert.match(css, /#screenSizePage \.preview-dpad \.dpad\{[\s\S]*?width:44px !important;[\s\S]*?height:44px !important;/);
  assert.match(xml, /@\+id\/dpadContainer[\s\S]*?layout_width="150dp"[\s\S]*?layout_height="150dp"/);
  assert.match(xml, /@\+id\/buttonUp[\s\S]*?layout_width="44dp"[\s\S]*?layout_height="44dp"/);

  assert.match(css, /#screenSizePage \.preview-start-select\{[\s\S]*?width:116px !important;[\s\S]*?height:38px !important;/);
  assert.match(css, /#screenSizePage \.preview-start-select span\{[\s\S]*?width:50px !important;[\s\S]*?height:24px !important;/);
  assert.match(xml, /@\+id\/startSelectContainer[\s\S]*?layout_width="116dp"[\s\S]*?layout_height="38dp"/);

  assert.match(css, /#screenSizePage \.preview-ab\{[\s\S]*?width:150px !important;[\s\S]*?height:94px !important;/);
  assert.match(css, /#screenSizePage \.preview-ab span\{[\s\S]*?width:54px !important;[\s\S]*?height:54px !important;/);
  assert.match(xml, /@\+id\/abContainer[\s\S]*?layout_width="150dp"[\s\S]*?layout_height="94dp"/);

  assert.match(css, /extra-control\.menu-style-utility[\s\S]*?width:44px !important;[\s\S]*?height:44px !important;/);
  assert.match(xml, /@\+id\/quickLoadButton[\s\S]*?layout_width="44dp"[\s\S]*?layout_height="44dp"/);

  assert.match(css, /data-control-type="buttonA"[\s\S]*?width:48px !important;[\s\S]*?height:48px !important;/);
  assert.match(kt, /FrameLayout\.LayoutParams\(dp\(48f\)\.roundToInt\(\), dp\(48f\)\.roundToInt\(\)\)/);
});
