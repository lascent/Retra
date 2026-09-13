const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const read = (p) => fs.readFileSync(p, 'utf8');

test('GitHub Actions installs the published Android 17 platform package', () => {
  const workflow = read('.github/workflows/regression.yml');
  assert.match(workflow, /RETRA_ANDROID_API:\s*["']37\.0["']/);
  assert.match(workflow, /platforms;android-\$\{RETRA_ANDROID_API\}/);
  assert.match(workflow, /--sdk_root="\$SDK_ROOT"\s+--install/);
  assert.doesNotMatch(workflow, /RETRA_ANDROID_API:\s*["']37["']/);
});

test('checked-in workflow copy stays synchronized on Android platform id', () => {
  const copy = read('regression.yml');
  assert.match(copy, /RETRA_ANDROID_API:\s*["']37\.0["']/);
  assert.match(copy, /--sdk_root="\$SDK_ROOT"\s+--install/);
});
