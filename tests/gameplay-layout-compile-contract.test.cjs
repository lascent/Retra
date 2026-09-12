const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/retra/emulator/GameplayLayoutController.kt'), 'utf8');

test('GameplayLayoutController imports extracted fast-forward and configuration symbols', () => {
  assert.match(source, /import android\.content\.res\.Configuration/);
  assert.match(source, /import com\.retra\.emulator\.MainActivity\.Companion\.FAST_FORWARD_BUTTON_MODE_PREF/);
  assert.match(source, /FAST_FORWARD_BUTTON_MODE_PREF/);
  assert.match(source, /Configuration\.ORIENTATION_LANDSCAPE/);
});
