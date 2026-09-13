const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const indexHtml = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/index.html'), 'utf8');
const settingsUi = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/settings-ui.js'), 'utf8');

function section(id) {
  const start = indexHtml.indexOf(`id="${id}"`);
  assert.notEqual(start, -1, `${id} should exist`);
  const next = indexHtml.indexOf('<section', start + 1);
  return indexHtml.slice(start, next === -1 ? undefined : next);
}

test('More About matches Settings About branding and v1.0.2 version', () => {
  const moreAbout = section('aboutPage');
  const settingsAbout = section('aboutSettingsPage');
  for (const html of [moreAbout, settingsAbout]) {
    assert.match(html, /class="about-panel"/);
    assert.match(html, /class="about-logo" src="assets\/branding\/retra-logo\.png"/);
    assert.match(html, /<h3>Retra<\/h3>/);
    assert.match(html, /Retra v1\.0\.2/);
    assert.match(html, /Privacy Policy/);
  }
  assert.doesNotMatch(moreAbout, /Retra v4\.26/);
});

test('Privacy Policy returns to the About page that opened it', () => {
  assert.match(indexHtml, /data-privacy-return="aboutSettingsPage"/);
  assert.match(indexHtml, /data-privacy-return="aboutPage"/);
  assert.match(indexHtml, /id="privacyPolicyBackBtn"/);
  assert.match(settingsUi, /privacyBackBtn\.dataset\.backTo = btn\.dataset\.privacyReturn/);
});
