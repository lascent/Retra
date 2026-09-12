(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  if (root) root.RetraLibrarySelection = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  function uniqueIds(values) {
    return [...new Set((values || []).map(value => String(value || '')).filter(Boolean))];
  }

  function toggleId(values, id) {
    const next = new Set(uniqueIds(values));
    const key = String(id || '');
    if (!key) return [...next];
    if (next.has(key)) next.delete(key);
    else next.add(key);
    return [...next];
  }

  function matchesCategory(assignments, category) {
    const list = Array.isArray(assignments)
      ? assignments.map(value => String(value || '').trim()).filter(Boolean)
      : [];
    const target = String(category || 'Default');
    if (target === 'All') return true; // Backward-compatible with older saved UI state.
    if (target === 'Default') return list.length === 0;
    return list.includes(target);
  }

  function shouldFavoriteAll(roms) {
    const items = Array.isArray(roms) ? roms.filter(Boolean) : [];
    return items.length > 0 && !items.every(rom => Boolean(rom.favorite));
  }

  function categoryState(assignmentsByRom, category) {
    const rows = Array.isArray(assignmentsByRom) ? assignmentsByRom : [];
    if (!rows.length) return 'none';
    const matches = rows.filter(assignments => matchesCategory(assignments, category)).length;
    if (matches === 0) return 'none';
    if (matches === rows.length) return 'all';
    return 'mixed';
  }

  return {
    uniqueIds,
    toggleId,
    matchesCategory,
    shouldFavoriteAll,
    categoryState
  };
});
