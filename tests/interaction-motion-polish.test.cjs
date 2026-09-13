const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('interaction and motion polish is loaded after core safe-area styling', () => {
  const html = read('app/src/main/assets/retra/index.html');
  const safe = html.indexOf('style-safe-insets.css');
  const motion = html.indexOf('style-motion-performance.css');
  assert.ok(safe >= 0 && motion > safe);
  assert.match(html, /<script src="interaction-motion\.js"><\/script>/);
});

test('motion layer uses compositor-friendly page/sheet properties', () => {
  const css = read('app/src/main/assets/retra/style-motion-performance.css');
  assert.match(css, /--retra-motion-press:\s*48ms/);
  assert.match(css, /\.retra-pressing/);
  assert.match(css, /transition:\s*transform var\(--retra-motion-sheet\)/);
  assert.match(css, /\.page\.retra-page-tab-enter/);
  assert.doesNotMatch(css, /transition\s*:\s*all\b/i);
  assert.match(css, /prefers-reduced-motion:\s*reduce/);
});

test('pointer feedback cancels when a drag becomes scrolling', () => {
  const js = read('app/src/main/assets/retra/interaction-motion.js');
  assert.match(js, /pointerdown/);
  assert.match(js, /pointermove/);
  assert.match(js, /MOVE_CANCEL_PX\s*=\s*11/);
  assert.match(js, /Math\.hypot/);
  assert.match(js, /retra-pressing/);
});

test('page switches invoke lightweight entrance motion after activation', () => {
  const ui = read('app/src/main/assets/retra/library-ui.js');
  assert.match(ui, /RetraMotion\?\.pageEnter\?\.\(nextPage, 'tab'\)/);
  assert.match(ui, /RetraMotion\?\.pageEnter\?\.\(nextPage, 'forward'\)/);
});
