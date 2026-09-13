const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('dedicated scroll performance layer loads last in CSS and before interaction JS', () => {
  const html = read('app/src/main/assets/retra/index.html');
  assert.ok(html.indexOf('style-scroll-performance.css') > html.indexOf('style-appearance-accent.css'));
  assert.ok(html.indexOf('scroll-performance.js') < html.indexOf('interaction-motion.js'));
});

test('scrolling stays native/passive and never uses JS interpolation', () => {
  const js = read('app/src/main/assets/retra/scroll-performance.js');
  assert.match(js, /addEventListener\('scroll', markScrollActivity, \{ capture: true, passive: true \}\)/);
  assert.match(js, /SCROLL_IDLE_MS\s*=\s*96/);
  assert.match(js, /RetraScrollPerformance/);
  assert.doesNotMatch(js, /scrollTo\s*\(/);
  assert.doesNotMatch(js, /scrollBy\s*\(/);
  assert.doesNotMatch(js, /requestAnimationFrame\([^)]*scroll/i);
});

test('Android scroll path removes expensive persistent nav blur and uses compositor-friendly containment', () => {
  const css = read('app/src/main/assets/retra/style-scroll-performance.css');
  assert.match(css, /data-retra-runtime="android"/);
  assert.match(css, /\.bottom-nav/);
  assert.match(css, /backdrop-filter:\s*none\s*!important/);
  assert.match(css, /\.history-item[\s\S]*content-visibility:\s*auto/);
  assert.match(css, /\.playtime-item[\s\S]*content-visibility:\s*auto/);
  assert.match(css, /touch-action:\s*pan-y/);
  assert.match(css, /touch-action:\s*pan-x/);
});

test('library virtualization starts before an 80-ROM stress case', () => {
  const ui = read('app/src/main/assets/retra/library-ui.js');
  assert.match(ui, /libraryRoms\.length\s*>=\s*36/);
  assert.doesNotMatch(ui, /libraryRoms\.length\s*>=\s*80/);
});

test('background maintenance defers while native scrolling is active', () => {
  const shell = read('app/src/main/assets/retra/app-shell.js');
  assert.match(shell, /RetraScrollPerformance\?\.runWhenIdle/);
  assert.match(shell, /requestIdleCallback/);
});

test('playtime cover images decode asynchronously at low priority', () => {
  const ui = read('app/src/main/assets/retra/library-ui.js');
  assert.match(ui, /image\.loading = 'lazy'/);
  assert.match(ui, /image\.decoding = 'async'/);
  assert.match(ui, /image\.fetchPriority = 'low'/);
});
