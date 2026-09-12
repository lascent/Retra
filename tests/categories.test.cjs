const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { readWebJs, readWebCss } = require('./helpers/assets.cjs');
const source = readWebJs(path.join(__dirname, '..'));
function runtime(values = {}, unavailable = false) {
  const context = vm.createContext({ localStorage: { getItem(key) {
    if (unavailable) throw new Error('Storage unavailable');
    return values[key] ?? null;
  } } });
  vm.runInContext(source.slice(0, source.indexOf('const pages =')), context);
  return context;
}
const plain = value => JSON.parse(JSON.stringify(value));
test('missing, malformed and wrong-shaped category data safely loads', () => {
  for (const value of [undefined, 'broken', 'null', '{}', '42', '"text"']) {
    assert.deepEqual(plain(runtime({ retraCategories: value }).readCustomCategories()), []);
  }
  for (const value of [undefined, 'broken', 'null', '[]', '42', '"text"']) {
    assert.deepEqual(plain(runtime({ assignments: value }).readCategoryMap('assignments')), {});
  }
});
test('valid names survive, duplicates and invalid entries are removed', () => {
  const value = JSON.stringify(['Action', 'Action', null, 42, '', '  ', 'RPG & Adventure']);
  assert.deepEqual(plain(runtime({ retraCategories: value }).readCustomCategories()), ['Action', 'RPG & Adventure']);
});
test('malformed per-ROM assignments cannot break array operations', () => {
  const value = JSON.stringify({ first: ['RPG', null, 'RPG'], second: 'RPG', third: null });
  assert.deepEqual(plain(runtime({ assignments: value }).readCategoryMap('assignments')), { first: ['RPG'], second: [], third: [] });
});
test('storage read failures do not crash category initialization', () => {
  const app = runtime({}, true);
  assert.deepEqual(plain(app.readCustomCategories()), []);
  assert.deepEqual(plain(app.readCategoryMap('assignments')), {});
});
test('category text cannot inject tags or attribute values', () => {
  assert.equal(runtime().escapeHtml('<img src=x onerror="run()"> & \'RPG\''), '&lt;img src=x onerror=&quot;run()&quot;&gt; &amp; &#39;RPG&#39;');
});
