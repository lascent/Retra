const { test } = require('node:test');
const assert = require('node:assert/strict');
const selection = require('../app/src/main/assets/retra/library-selection.js');

test('Default category contains only ROMs without custom categories', () => {
  assert.equal(selection.matchesCategory([], 'Default'), true);
  assert.equal(selection.matchesCategory(['RPG'], 'Default'), false);
  assert.equal(selection.matchesCategory(['RPG'], 'RPG'), true);
  assert.equal(selection.matchesCategory([], 'RPG'), false);
});

test('selection toggles IDs without duplicates', () => {
  assert.deepEqual(selection.toggleId([], 'a'), ['a']);
  assert.deepEqual(selection.toggleId(['a'], 'a'), []);
  assert.deepEqual(selection.toggleId(['a', 'a', 'b'], 'c'), ['a', 'b', 'c']);
});

test('favorite bulk action adds favorite unless every selected ROM is already favorite', () => {
  assert.equal(selection.shouldFavoriteAll([{ favorite: false }, { favorite: true }]), true);
  assert.equal(selection.shouldFavoriteAll([{ favorite: true }, { favorite: true }]), false);
  assert.equal(selection.shouldFavoriteAll([]), false);
});

test('category state reports all, mixed and none', () => {
  assert.equal(selection.categoryState([['RPG'], ['RPG', 'Action']], 'RPG'), 'all');
  assert.equal(selection.categoryState([['RPG'], []], 'RPG'), 'mixed');
  assert.equal(selection.categoryState([[], []], 'RPG'), 'none');
  assert.equal(selection.categoryState([[], []], 'Default'), 'all');
  assert.equal(selection.categoryState([[], ['RPG']], 'Default'), 'mixed');
});
