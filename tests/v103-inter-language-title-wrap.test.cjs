const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');
const html = read('app/src/main/assets/retra/index.html');
const settings = read('app/src/main/assets/retra/settings-ui.js');
const coreCss = read('app/src/main/assets/retra/style-core.css');
const polishCss = read('app/src/main/assets/retra/style-layout-polish.css');
const uiCss = read('app/src/main/assets/retra/ui-polish.css');
const i18n = read('app/src/main/assets/retra/i18n.js');
const nativeLanguage = read('app/src/main/java/com/retra/emulator/UiLanguage.kt');
const gameplay = read('app/src/main/java/com/retra/emulator/GameplayController.kt');

test('Inter is the v1.0.3 default while Poppins remains an optional font', () => {
  assert.match(coreCss, /font-family:var\(--app-font, Inter,/);
  assert.match(polishCss, /--app-font:Inter,/);
  assert.match(settings, /localStorage\.getItem\(fontStorageKey\) \|\| 'Inter'/);
  assert.match(settings, /retraInterDefaultMigrationV103/);
  assert.match(html, /font-option active" data-font-name="Inter"/);
  assert.match(html, /font-option" data-font-name="Poppins"/);
});

test('compact-grid ROM titles are capped at two lines for dense items-per-row layouts', () => {
  const rule = uiCss.match(/library-display-compact \.cover-info strong\{[\s\S]*?\n\}/)?.[0] || '';
  assert.match(rule, /white-space:normal/);
  assert.match(rule, /overflow-wrap:anywhere/);
  assert.match(rule, /-webkit-line-clamp:2/);
  assert.match(rule, /max-height:calc\(1\.35em \* 2\)/);
  assert.doesNotMatch(rule, /white-space:nowrap/);
  assert.match(uiCss, /data-items-per-row="5"/);
  assert.match(uiCss, /data-items-per-row="6"/);
});

test('Settings exposes Language directly below Fonts with English, Vietnamese and Indonesian', () => {
  const fontsPos = html.indexOf('data-setting="Fonts"');
  const languagePos = html.indexOf('data-setting="Language"');
  const aboutPos = html.indexOf('data-setting="About"');
  assert.ok(fontsPos >= 0 && languagePos > fontsPos && aboutPos > languagePos);
  assert.match(html, /id="languageSettingsPage"/);
  assert.match(html, /data-language-code="en"/);
  assert.match(html, /data-language-code="vi"/);
  assert.match(html, /data-language-code="id"/);
  assert.match(settings, /Language: 'languageSettingsPage'/);
  assert.match(html, /<script src="i18n\.js"><\/script>/);
});

test('Vietnamese and Indonesian translations are persisted and cover core Retra UI', () => {
  assert.match(i18n, /const STORAGE_KEY = 'retraUiLanguage'/);
  assert.match(i18n, /nativeSet\('language', currentLanguage\)/);
  assert.match(i18n, /'Library':'Thư viện'/);
  assert.match(i18n, /'Settings':'Cài đặt'/);
  assert.match(i18n, /'Language':'Ngôn ngữ'/);
  assert.match(i18n, /'Library':'Pustaka'/);
  assert.match(i18n, /'Settings':'Pengaturan'/);
  assert.match(i18n, /'Language':'Bahasa'/);
  assert.match(i18n, /MutationObserver/);
  assert.match(i18n, /userContentSelector/);
});

test('native gameplay menu follows the selected Web UI language preference', () => {
  assert.match(nativeLanguage, /prefs\.getString\("ui_language", "en"\)/);
  assert.match(nativeLanguage, /"Rewind" to "Tua ngược"/);
  assert.match(nativeLanguage, /"Edit layout" to "Chỉnh bố cục"/);
  assert.match(nativeLanguage, /"Import save" to "Impor simpanan"/);
  assert.match(gameplay, /text = uiText\(title\)/);
  assert.match(gameplay, /text = uiText\(subtitle\)/);
});
