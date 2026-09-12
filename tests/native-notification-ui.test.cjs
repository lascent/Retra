const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const javaRoot = path.join(root, 'app/src/main/java/com/retra/emulator');
const kotlinFiles = fs.readdirSync(javaRoot).filter((name) => name.endsWith('.kt'));
const kotlinSource = kotlinFiles.map((name) => fs.readFileSync(path.join(javaRoot, name), 'utf8')).join('\n');
const notifier = fs.readFileSync(path.join(javaRoot, 'RetraNotice.kt'), 'utf8');
const appShell = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/app-shell.js'), 'utf8');
const styleCore = fs.readFileSync(path.join(root, 'app/src/main/assets/retra/style-core.css'), 'utf8');

test('Retra contains no Android platform Toast calls', () => {
  assert.doesNotMatch(kotlinSource, /android\.widget\.Toast/);
  assert.doesNotMatch(kotlinSource, /\bToast\.makeText\b/);
  assert.doesNotMatch(kotlinSource, /\bToast\.LENGTH_(?:SHORT|LONG)\b/);
});

test('native notices use Retra UI and fall back above native gameplay', () => {
  assert.match(notifier, /typeof showToast === 'function'/);
  assert.match(notifier, /findViewById<FrameLayout>\(android\.R\.id\.content\)/);
  assert.match(notifier, /Color\.rgb\(37, 39, 65\)/);
  assert.match(notifier, /Kind\.SUCCESS -> Color\.rgb\(75, 201, 137\)/);
  assert.match(notifier, /Kind\.ERROR -> Color\.rgb\(238, 101, 113\)/);
  assert.match(notifier, /Gravity\.BOTTOM or Gravity\.CENTER_HORIZONTAL/);
  assert.match(notifier, /ViewGroup\.LayoutParams\.WRAP_CONTENT/);
});

test('native notice layout margins compile without Kotlin name shadowing', () => {
  assert.match(notifier, /val noticeBottomMargin = navInset \+ dp\(activity, 82\)/);
  assert.match(notifier, /this\.bottomMargin = noticeBottomMargin/);
  assert.doesNotMatch(notifier, /val bottomMargin[\s\S]{0,400}bottomMargin = bottomMargin/);
});

test('web notices share the same Retra status treatment', () => {
  assert.match(appShell, /function inferToastKind\(message\)/);
  assert.match(appShell, /function showToast\(message, kind = 'auto', duration = 2300\)/);
  assert.match(styleCore, /\.toast\.success\{--notice-accent:#4bc989\}/);
  assert.match(styleCore, /\.toast\.error\{--notice-accent:#ee6571\}/);
  assert.match(styleCore, /text-align:center/);
  assert.match(styleCore, /border-radius:14px/);
  assert.match(styleCore, /border:1px solid var\(--notice-accent\)/);
});


test('all Retra notices are content-sized and centered instead of full-width bars', () => {
  assert.match(notifier, /val maxNoticeWidth =/);
  assert.match(notifier, /maxWidth = \(maxNoticeWidth - dp\(activity, 32\)\)/);
  assert.match(notifier, /ViewGroup\.LayoutParams\.WRAP_CONTENT/);
  assert.match(styleCore, /width:max-content/);
  assert.match(styleCore, /min-width:0/);
  assert.doesNotMatch(styleCore, /\.toast::before/);
});
