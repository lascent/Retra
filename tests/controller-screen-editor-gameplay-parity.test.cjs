const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('Screen Editor loads the gameplay controller parity layer last', () => {
  const html = read('app/src/main/assets/retra/index.html');
  assert.match(html, /style-gameplay-controller-parity\.css/);
  assert.ok(
    html.indexOf('style-gameplay-controller-parity.css') > html.indexOf('style-scroll-performance.css'),
    'controller parity CSS must load after all appearance/editor layers'
  );
});

test('Screen Editor controller geometry and glyphs mirror native gameplay resources', () => {
  const css = read('app/src/main/assets/retra/style-gameplay-controller-parity.css');
  const layout = read('app/src/main/res/layout/activity_main.xml');
  const dpad = read('app/src/main/res/drawable/ic_controller_dpad_up.xml');
  const menu = read('app/src/main/res/drawable/ic_controller_menu.xml');

  assert.match(layout, /android:id="@\+id\/buttonL"[\s\S]*?android:layout_width="72dp"[\s\S]*?android:layout_height="30dp"/);
  assert.match(css, /\.preview-shoulder[\s\S]*?width:\s*72px !important;[\s\S]*?height:\s*30px !important;/);
  assert.match(layout, /android:id="@\+id\/dpadContainer"[\s\S]*?android:layout_width="150dp"[\s\S]*?android:layout_height="150dp"/);
  assert.match(css, /\.preview-dpad \{[\s\S]*?width:\s*150px !important;[\s\S]*?height:\s*150px !important;/);
  assert.match(dpad, /M1,11\.5 L8,4\.5 L15,11\.5/);
  assert.match(css, /M1 11\.5 L8 4\.5 L15 11\.5/);
  assert.match(menu, /M5\.75,7\.5 L18\.25,7\.5 M5\.75,12 L18\.25,12 M5\.75,16\.5 L18\.25,16\.5/);
  assert.match(css, /stroke-width:\s*2\.45 !important/);
});

test('Add-control restore thumbnails no longer substitute generic D-pad or Start\/Select logos', () => {
  const js = read('app/src/main/assets/retra/controller-editor-ui.js');
  assert.match(js, /native-dpad-preview/);
  assert.match(js, /native-start-select-preview/);
  assert.match(js, /native-shoulder-preview/);
  assert.doesNotMatch(js, /case 'dpad':[\s\S]{0,220}M10 3h4v7h7v4h-7v7h-4v-7H3v-4h7z/);
  assert.doesNotMatch(js, /case 'startSelect':[\s\S]{0,160}>SS</);
});

test('native gameplay L/R use asymmetric curved shoulder drawables while Start/Select keep the pill', () => {
  const layout = read('app/src/main/res/layout/activity_main.xml');
  const left = read('app/src/main/res/drawable/bg_controller_shoulder_left.xml');
  const right = read('app/src/main/res/drawable/bg_controller_shoulder_right.xml');

  assert.match(layout, /android:id="@\+id\/buttonL"[\s\S]*?android:background="@drawable\/bg_controller_shoulder_left"/);
  assert.match(layout, /android:id="@\+id\/buttonR"[\s\S]*?android:background="@drawable\/bg_controller_shoulder_right"/);
  assert.match(layout, /android:id="@\+id\/buttonSelect"[\s\S]*?android:background="@drawable\/bg_controller_pill"/);
  assert.match(layout, /android:id="@\+id\/buttonStart"[\s\S]*?android:background="@drawable\/bg_controller_pill"/);

  assert.match(left, /android:topLeftRadius="12dp"/);
  assert.match(left, /android:topRightRadius="28dp"/);
  assert.match(left, /android:bottomRightRadius="16dp"/);
  assert.match(left, /android:bottomLeftRadius="12dp"/);
  assert.match(right, /android:topLeftRadius="28dp"/);
  assert.match(right, /android:topRightRadius="12dp"/);
  assert.match(right, /android:bottomRightRadius="12dp"/);
  assert.match(right, /android:bottomLeftRadius="16dp"/);
});
