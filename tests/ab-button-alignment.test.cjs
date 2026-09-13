const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const xml = fs.readFileSync(path.join(root, 'app/src/main/res/layout/activity_main.xml'), 'utf8');
const kotlin = fs.readFileSync(path.join(root, 'app/src/main/java/com/retra/emulator/GameplayLayoutController.kt'), 'utf8');
const features = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-features.css'), 'utf8');

test('native grouped A/B buttons use the same vertical centerline', () => {
  assert.match(xml, /@\+id\/buttonB[\s\S]*?layout_gravity="center_vertical\|start"/);
  assert.match(xml, /@\+id\/buttonA[\s\S]*?layout_gravity="center_vertical\|end"/);
  assert.match(kotlin, /gravity = Gravity\.CENTER_VERTICAL or if \(isA\) Gravity\.END else Gravity\.START/);
});

test('screen editor preview keeps A and B on the same row', () => {
  assert.match(features, /\.button-b\{left:0 !important; top:20px !important; bottom:auto !important;\}/);
  assert.match(features, /\.button-a\{left:auto !important; right:0 !important; top:20px !important; bottom:auto !important;\}/);
});
