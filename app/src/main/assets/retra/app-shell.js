// Read category metadata defensively without clearing or rewriting stored data.
function readCategoryMap(key){
  try {
    const value = JSON.parse(localStorage.getItem(key) || '{}');
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
    return Object.fromEntries(Object.entries(value).map(([id, categories]) => [
      id, Array.isArray(categories) ? [...new Set(categories.filter(item => typeof item === 'string'))] : []
    ]));
  } catch (_) {
    return {};
  }
}

function readCustomCategories(){
  try {
    const value = JSON.parse(localStorage.getItem('retraCategories') || '[]');
    return Array.isArray(value)
      ? [...new Set(value.filter(item => typeof item === 'string' && item.trim()))]
      : [];
  } catch (_) {
    return [];
  }
}

function escapeHtml(value){
  return String(value).replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

const pages = document.querySelectorAll('.page');
const headers = {
  libraryPage: document.getElementById('libraryHeader'),
  historyPage: document.getElementById('historyHeader'),
  morePage: document.getElementById('moreHeader')
};

const navItems = document.querySelectorAll('.nav-item');
const searchBtn = document.getElementById('searchBtn');
const historySearchBtn = document.getElementById('historySearchBtn');
const libraryMoreBtn = document.getElementById('libraryMoreBtn');
const libraryOverflowMenu = document.getElementById('libraryOverflowMenu');
const refreshLibraryBtn = document.getElementById('refreshLibraryBtn');
const addRomBtn = document.getElementById('addRomBtn');
const romFileInput = document.getElementById('romFileInput');
const libraryGrid = document.getElementById('libraryGrid');
const clearHistoryBtn = document.getElementById('clearHistoryBtn');
const morePageBtn = document.getElementById('morePageBtn');
const searchPanel = document.getElementById('searchPanel');
const searchInput = document.getElementById('searchInput');
let chips = document.querySelectorAll('.chip');
const emptyState = document.getElementById('emptyState');
const historyList = document.getElementById('historyList');
const historyEmpty = document.getElementById('historyEmpty');
const incognitoHistoryToggle = document.getElementById('incognitoHistoryToggle');
const toast = document.getElementById('toast');
const appMain = document.getElementById('appMain');
const confirmModal = document.getElementById('confirmModal');
const confirmTitle = document.getElementById('confirmTitle');
const confirmMessage = document.getElementById('confirmMessage');
const confirmCancelBtn = document.getElementById('confirmCancelBtn');
const confirmOkBtn = document.getElementById('confirmOkBtn');
const romCardActionsModal = document.getElementById('romCardActionsModal');
const romCardActionsCover = document.getElementById('romCardActionsCover');
const romCardActionsTitle = document.getElementById('romCardActionsTitle');
const romFavoriteAction = document.getElementById('romFavoriteAction');
const romFavoriteActionLabel = document.getElementById('romFavoriteActionLabel');
const romRemoveAction = document.getElementById('romRemoveAction');
const romDeleteDataAction = document.getElementById('romDeleteDataAction');
const closeRomCardActions = document.getElementById('closeRomCardActions');
const librarySelectionBar = document.getElementById('librarySelectionBar');
const librarySelectionClose = document.getElementById('librarySelectionClose');
const librarySelectionCount = document.getElementById('librarySelectionCount');
const librarySelectionCategories = document.getElementById('librarySelectionCategories');
const librarySelectionFavorite = document.getElementById('librarySelectionFavorite');
const librarySelectionFavoriteLabel = document.getElementById('librarySelectionFavoriteLabel');
const librarySelectionMore = document.getElementById('librarySelectionMore');
const librarySelectionMoreMenu = document.getElementById('librarySelectionMoreMenu');
const librarySelectionRemove = document.getElementById('librarySelectionRemove');
const multiCategoryModal = document.getElementById('multiCategoryModal');
const multiCategoryCopy = document.getElementById('multiCategoryCopy');
const multiCategoryList = document.getElementById('multiCategoryList');
const multiCategoryCancel = document.getElementById('multiCategoryCancel');
const multiCategorySave = document.getElementById('multiCategorySave');

const librarySelectionApi = window.RetraLibrarySelection || {
  matchesCategory(assignments, category){
    const list = Array.isArray(assignments) ? assignments.filter(Boolean) : [];
    if (category === 'All') return true;
    if (category === 'Default') return list.length === 0;
    return list.includes(category);
  },
  shouldFavoriteAll(roms){
    return Array.isArray(roms) && roms.length > 0 && !roms.every(rom => Boolean(rom.favorite));
  },
  categoryState(rows, category){
    if (!Array.isArray(rows) || !rows.length) return 'none';
    const count = rows.filter(row => this.matchesCategory(row, category)).length;
    return count === 0 ? 'none' : count === rows.length ? 'all' : 'mixed';
  }
};

const detailPage = document.getElementById('romDetailPage');

function updateFloatingAddRomButton(pageId){
  const shouldShow = pageId === 'libraryPage';
  document.body.classList.toggle('library-add-rom-visible', shouldShow);
}

function updateRomDetailState(pageId){
  const romDetailOpen = pageId === 'romDetailPage';
  document.body.classList.toggle('rom-detail-open', romDetailOpen);
}
const romHero = document.getElementById('romHero');
const detailCover = document.getElementById('detailCover');
const detailTitle = document.getElementById('detailTitle');
const detailAuthor = document.getElementById('detailAuthor');
const detailStudio = document.getElementById('detailStudio');
const detailStatus = document.getElementById('detailStatus');
const detailSource = document.getElementById('detailSource');
const detailSystem = document.getElementById('detailSystem');
const detailDescription = document.getElementById('detailDescription');
const detailChips = document.getElementById('detailChips');
const detailEntries = document.getElementById('detailEntries');
const entriesCount = document.getElementById('entriesCount');
const bgFileInput = document.getElementById('bgFileInput');
const coverFileInput = document.getElementById('coverFileInput');
const romFilterBtn = document.getElementById('romFilterBtn');
const menuEditNameBtn = document.getElementById('menuEditNameBtn');
const menuChangeBgBtn = document.getElementById('menuChangeBgBtn');
const menuChangeCoverBtn = document.getElementById('menuChangeCoverBtn');
const romOverflowMenu = document.getElementById('romOverflowMenu');
const romFilterSheetBackdrop = document.getElementById('romFilterSheetBackdrop');
const sheetCategoryList = document.getElementById('sheetCategoryList');
const sheetCategoryEmpty = document.getElementById('sheetCategoryEmpty');
const sheetRomTitle = document.getElementById('sheetRomTitle');
const closeRomFilterSheet = document.getElementById('closeRomFilterSheet');
const saveRomCategoriesBtn = document.getElementById('saveRomCategoriesBtn');
const romFilterMoreBtn = document.getElementById('romFilterMoreBtn');
const romFilterOptionsMenu = document.getElementById('romFilterOptionsMenu');
const setFilterDefaultBtn = document.getElementById('setFilterDefaultBtn');
const resetFilterDefaultBtn = document.getElementById('resetFilterDefaultBtn');
const resumeAction = document.getElementById('resumeAction');
const bigResumeBtn = document.getElementById('bigResumeBtn');
const romMenuBtn = document.getElementById('romMenuBtn');

let currentRomCard = null;

function activeLayoutRomId(){
  return String(currentRomCard?.dataset?.romId || '');
}

function nativeGetGameplayLayout(orientation){
  if (!window.AndroidBridge) return '{}';
  const romId = activeLayoutRomId();
  if (romId && typeof window.AndroidBridge.getRomGameplayLayout === 'function') {
    return window.AndroidBridge.getRomGameplayLayout(romId, orientation);
  }
  if (typeof window.AndroidBridge.getGameplayLayout === 'function') {
    return window.AndroidBridge.getGameplayLayout(orientation);
  }
  return '{}';
}

function nativeSetControllerLayout(json){
  if (!window.AndroidBridge) return;
  const romId = activeLayoutRomId();
  if (romId && typeof window.AndroidBridge.setRomControllerLayout === 'function') {
    window.AndroidBridge.setRomControllerLayout(romId, json);
  } else if (typeof window.AndroidBridge.setControllerLayout === 'function') {
    window.AndroidBridge.setControllerLayout(json);
  }
}

function nativeSetScreenLayout(json){
  if (!window.AndroidBridge) return;
  const romId = activeLayoutRomId();
  if (romId && typeof window.AndroidBridge.setRomEmulatorScreenLayout === 'function') {
    window.AndroidBridge.setRomEmulatorScreenLayout(romId, json);
  } else if (typeof window.AndroidBridge.setEmulatorScreenLayout === 'function') {
    window.AndroidBridge.setEmulatorScreenLayout(json);
  }
}

function nativeCommitGameplayLayout(controllerJson, screenJson){
  if (!window.AndroidBridge) return false;
  const romId = activeLayoutRomId();
  if (romId && typeof window.AndroidBridge.commitRomGameplayLayout === 'function') {
    return Boolean(window.AndroidBridge.commitRomGameplayLayout(romId, controllerJson, screenJson));
  }
  if (typeof window.AndroidBridge.commitGameplayLayout === 'function') {
    return Boolean(window.AndroidBridge.commitGameplayLayout(controllerJson, screenJson));
  }
  return false;
}

function nativeClearGameplayLayout(orientation){
  if (!window.AndroidBridge) return false;
  const romId = activeLayoutRomId();
  if (romId && typeof window.AndroidBridge.clearRomGameplayLayout === 'function') {
    return Boolean(window.AndroidBridge.clearRomGameplayLayout(romId, orientation));
  }
  if (typeof window.AndroidBridge.clearGameplayLayout === 'function') {
    return Boolean(window.AndroidBridge.clearGameplayLayout(orientation));
  }
  return false;
}

let activeSearchContext = 'library';
let confirmAction = null;

// Main-tab navigation is intentionally stateful. Home/Library stays mounted in
// the DOM and keeps its scroll/search/category state while the user visits
// History, More, or a detail/settings page. This avoids rebuilding the ROM grid
// or showing an empty frame when returning Home.
const mainTabPageIds = new Set(['libraryPage', 'historyPage', 'morePage']);
const pageUiStateStorageKey = 'retraPageUiStateV1';
const pageUiState = new Map();

try {
  const storedPageState = JSON.parse(sessionStorage.getItem(pageUiStateStorageKey) || '{}');
  if (storedPageState && typeof storedPageState === 'object') {
    Object.entries(storedPageState).forEach(([pageId, state]) => {
      if (state && typeof state === 'object') pageUiState.set(pageId, state);
    });
  }
} catch (_) {}

function activePageId(){
  return document.querySelector('.page.active')?.id || 'libraryPage';
}

function persistPageUiState(){
  try { sessionStorage.setItem(pageUiStateStorageKey, JSON.stringify(Object.fromEntries(pageUiState))); } catch (_) {}
}

function capturePageUiState(pageId = activePageId()){
  if (!pageId) return;
  const state = { ...(pageUiState.get(pageId) || {}) };
  state.scrollTop = Math.max(0, Number(appMain?.scrollTop) || 0);

  if (pageId === 'libraryPage' || pageId === 'historyPage') {
    state.searchOpen = Boolean(searchPanel?.classList.contains('open'));
    state.searchQuery = searchInput?.value || '';
  }
  if (pageId === 'libraryPage') {
    state.category = document.querySelector('#libraryChips .chip.active')?.dataset.category || 'Default';
  }

  pageUiState.set(pageId, state);
  persistPageUiState();
}

function restorePageUiState(pageId){
  const state = pageUiState.get(pageId) || {};

  if (pageId === 'libraryPage' || pageId === 'historyPage') {
    updateSearchContext(pageId);
    if (searchInput) searchInput.value = typeof state.searchQuery === 'string' ? state.searchQuery : '';
    searchPanel?.classList.toggle('open', Boolean(state.searchOpen));
  } else {
    searchPanel?.classList.remove('open');
    searchInput?.blur();
  }

  if (pageId === 'libraryPage' && state.category) {
    const wanted = [...document.querySelectorAll('#libraryChips .chip')]
      .find(chip => chip.dataset.category === state.category);
    if (wanted) {
      document.querySelector('#libraryChips .chip.active')?.classList.remove('active');
      wanted.classList.add('active');
    }
  }

  // Restore after the active page has participated in layout. Using a frame
  // callback prevents a visible jump and keeps tab changes synchronous.
  requestAnimationFrame(() => {
    if (activePageId() !== pageId || !appMain) return;
    appMain.scrollTop = Math.max(0, Number(state.scrollTop) || 0);
  });
}

function scheduleUiBackgroundTask(callback, timeout = 700){
  const scheduleIdleWork = () => {
    if (typeof requestIdleCallback === 'function') {
      return requestIdleCallback(() => callback(), { timeout });
    }
    return window.setTimeout(callback, 32);
  };

  // Artwork lookup, metadata hydration and similar maintenance should never
  // compete with an active finger/momentum scroll for the WebView main thread.
  if (window.RetraScrollPerformance?.runWhenIdle) {
    return window.RetraScrollPerformance.runWhenIdle(scheduleIdleWork, timeout);
  }
  return scheduleIdleWork();
}

function openConfirmModal(title, message, onConfirm){
  if (!confirmModal || !confirmTitle || !confirmMessage) return;
  confirmTitle.textContent = title;
  confirmMessage.textContent = message;
  confirmAction = typeof onConfirm === 'function' ? onConfirm : null;
  confirmModal.classList.add('open');
}

function closeConfirmModal(){
  confirmModal?.classList.remove('open');
  confirmAction = null;
}

confirmOkBtn?.addEventListener('click', () => {
  const action = confirmAction;
  closeConfirmModal();
  if (action) action();
});

function inferToastKind(message){
  const normalized = String(message || '').toLowerCase();
  if (['could not','failed','failure','error','invalid','expired','unavailable','unsupported','not granted','not recognized'].some((part) => normalized.includes(part))) return 'error';
  if (['disabled','requires','choose ','turn ','allow ','pair ','empty','no quick','not supported','different rom'].some((part) => normalized.includes(part))) return 'warning';
  if (['saved','selected','connected','complete','exported','deleted','reset','added','installed','player ','reconnected','updated','restored'].some((part) => normalized.includes(part))) return 'success';
  return 'info';
}

function showToast(message, kind = 'auto', duration = 2300){
  if (!toast) return;
  const originalMessage = String(message ?? '');
  const resolvedKind = kind === 'auto' ? inferToastKind(originalMessage) : kind;
  const localizedMessage = window.retraI18n?.translateMessage
    ? window.retraI18n.translateMessage(originalMessage)
    : originalMessage;
  toast.textContent = localizedMessage;
  toast.classList.remove('info','success','warning','error');
  toast.classList.add(resolvedKind, 'show');
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.classList.remove('show'), Math.max(1200, Number(duration) || 2300));
}


function hasAndroidBridge(){
  return !!(window.AndroidBridge && typeof window.AndroidBridge.importRom === 'function');
}

function importNativeRom(){
  if (hasAndroidBridge()) {
    window.AndroidBridge.importRom();
    return true;
  }
  return false;
}

function nativeHasResumeState(romId){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.hasResumeState === 'function')) return false;
  try {
    return Boolean(window.AndroidBridge.hasResumeState(String(romId || '')));
  } catch (error) {
    return false;
  }
}

function updateRomLaunchAction(card = currentRomCard){
  if (!resumeAction || !card) return false;
  const fileAvailable = card.dataset.fileAvailable !== 'false';
  const canResume = fileAvailable && nativeHasResumeState(card.dataset.romId || '');
  const label = resumeAction.querySelector('span');
  if (!fileAvailable) {
    if (label) label.textContent = 'Add ROM to Play';
    resumeAction.dataset.launchMode = 'locate';
    resumeAction.setAttribute('aria-label', 'Add matching ROM to play');
    return false;
  }
  if (label) label.textContent = canResume ? 'Resume' : 'Play';
  resumeAction.dataset.launchMode = canResume ? 'resume' : 'play';
  resumeAction.setAttribute('aria-label', canResume ? 'Resume game' : 'Play game');
  return canResume;
}

function launchNativeRom(card, resume = false){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.launchRom === 'function')) return false;
  if (!card) return false;

  // History is committed only after Android confirms that the ROM actually
  // started. Keep the pending id so the successful native callback can update
  // the exact library item without relying on title matching.
  pendingLaunchHistoryRomId = card.dataset.romId || null;

  const args = [
    card.dataset.romId || '',
    card.dataset.title || 'ROM',
    card.dataset.fileName || '',
    card.dataset.system || 'ROM'
  ];

  if (resume && typeof window.AndroidBridge.resumeRom === 'function') {
    window.AndroidBridge.resumeRom(...args);
  } else {
    window.AndroidBridge.launchRom(...args);
  }
  return true;
}

window.retraNativeFileImported = function(metaJson){
  try {
    const meta = JSON.parse(metaJson);
    if (!meta || !meta.id) return;

    const incoming = {
      id: String(meta.id),
      title: String(meta.title || 'ROM'),
      fileName: String(meta.fileName || 'ROM'),
      size: Number(meta.size) || 0,
      lastModified: Number(meta.lastModified) || Date.now(),
      system: String(meta.system || 'ROM'),
      addedAt: Date.now(),
      native: true,
      fileAvailable: true
    };

    const existingIndex = libraryRoms.findIndex(rom => rom.id === incoming.id);
    if (existingIndex >= 0) {
      // Exact hash re-imports reuse the same permanent romId. Merge file
      // metadata without disturbing favourites, categories, covers or stats.
      libraryRoms[existingIndex] = { ...libraryRoms[existingIndex], ...incoming, archived: false };
    } else {
      const archivedIndex = archivedLibraryRoms.findIndex(rom => rom.id === incoming.id);
      if (archivedIndex >= 0) {
        const archived = archivedLibraryRoms.splice(archivedIndex, 1)[0];
        libraryRoms.unshift({ ...archived, ...incoming, archived: false });
        saveArchivedLibraryRoms();
      } else {
        const duplicateIndex = libraryRoms.findIndex(rom =>
          rom.fileName === incoming.fileName &&
          Number(rom.size) === Number(incoming.size)
        );
        if (duplicateIndex >= 0 && meta.replaceExisting) {
          libraryRoms[duplicateIndex] = { ...libraryRoms[duplicateIndex], ...incoming, archived: false };
        } else if (duplicateIndex < 0) {
          libraryRoms.unshift({ ...incoming, archived: false });
          applyDefaultCategoriesToRomKey(incoming.id);
        }
      }
    }

    saveLibraryRoms();
    renderLibraryFromStorage();
    lookupAutomaticRomCover(getRomById(incoming.id));
    // Successful ROM imports are intentionally silent; the updated Library is the feedback.
  } catch (error) {
    showToast('Could not add imported file');
  }
};

window.retraNativeRomStarted = function(title, system){
  const pendingId = pendingLaunchHistoryRomId;
  pendingLaunchHistoryRomId = null;

  const rom = pendingId
    ? getRomById(pendingId)
    : libraryRoms.find(item => (item.title || '') === (title || '') && (!system || (item.system || '') === system));

  if (rom) recordHistoryPlay(rom, title, system);
};

window.retraNativeSaveStatesChanged = function(romId){
  const id = String(romId || '');
  if (currentRomCard && String(currentRomCard.dataset.romId || '') === id) {
    refreshRomRecentSaves(currentRomCard);
    updateRomLaunchAction(currentRomCard);
  }
  updateStatistics();
};

window.retraNativeGameClosed = function(romId){
  const id = String(romId || '');
  if (currentRomCard && String(currentRomCard.dataset.romId || '') === id) {
    // closeEmulator() has already written the dedicated auto-resume state.
    // Turn the first-launch Play action into Resume immediately.
    updateRomLaunchAction(currentRomCard);
    refreshRomRecentSaves(currentRomCard);
  }
};

window.retraOpenInGameSettings = function(){
  document.body.classList.add('in-game-settings');
  document.body.classList.remove('in-game-layout-editor');
  openSubPage('settingsHomePage');
};

window.retraOpenInGameLayoutEditor = function(){
  document.body.classList.add('in-game-settings', 'in-game-layout-editor');
  const back = document.querySelector('#screenSizePage .back-btn');
  if (back) back.dataset.backTo = 'settingsHomePage';
  openSubPage('screenSizePage');
};

window.retraCloseInGameSettings = function(){
  document.body.classList.remove('in-game-settings', 'in-game-layout-editor');
};

window.retraHandleAndroidBack = function(){
  const activePage = document.querySelector('.page.active');
  if (activePage?.id === 'screenSizePage' && typeof commitScreenEditorState === 'function') {
    commitScreenEditorState();
  }
  if (document.body.classList.contains('in-game-settings') && activePage?.id === 'settingsHomePage') {
    if (window.AndroidBridge && typeof window.AndroidBridge.closeInGameSettings === 'function') {
      window.AndroidBridge.closeInGameSettings();
      return true;
    }
  }
  const backButton = activePage?.querySelector('[data-back-to]');
  if (backButton) {
    backButton.click();
    return true;
  }
  return false;
};

function clearActiveHeaders(){
  Object.values(headers).forEach(h => h.classList.remove('active-header'));
}

function updateSearchContext(pageId){
  if (pageId === 'libraryPage') {
    activeSearchContext = 'library';
    searchInput.placeholder = 'Search ROMs...';
  } else if (pageId === 'historyPage') {
    activeSearchContext = 'history';
    searchInput.placeholder = 'Search history...';
  }
}

function toggleSearch(context){
  activeSearchContext = context;
  const pageId = context === 'history' ? 'historyPage' : 'libraryPage';
  searchInput.placeholder = context === 'history' ? 'Search history...' : 'Search ROMs...';
  searchPanel.classList.toggle('open');

  const state = { ...(pageUiState.get(pageId) || {}) };
  state.searchOpen = searchPanel.classList.contains('open');
  if (!state.searchOpen) {
    searchInput.value = '';
    state.searchQuery = '';
  } else {
    state.searchQuery = searchInput.value;
    requestAnimationFrame(() => searchInput.focus({ preventScroll: true }));
  }
  pageUiState.set(pageId, state);
  persistPageUiState();
  runActiveSearch();
}

function runActiveSearch(){
  if (activeSearchContext === 'history') {
    filterHistory();
  } else {
    filterCards();
  }
}

function normalizeRomName(value){
  return (value || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

function inferGenreChips(data){
  const tags = [];
  const system = (data.system || 'ROM').toUpperCase();
  const fileName = data.fileName || data.title || '';

  if (/\b(hack|mod|randomizer|redux|remix)\b/i.test(fileName)) tags.push('ROM Hack');
  else if (system === 'PATCH') tags.push('ROM Patch');
  else tags.push('Local ROM');

  if (system && system !== 'ROM' && system !== 'ARCHIVE') tags.push(system);
  if (system === 'ARCHIVE') tags.push('Archive');

  return [...new Set(tags)].slice(0, 4);
}

function getLibraryCards(){
  return [...document.querySelectorAll('#libraryGrid .cover-card')];
}

const romLibraryStorageKey = 'retraLibraryRomsV1';
const archivedRomLibraryStorageKey = 'retraArchivedLibraryRomsV1';
const romDatabaseName = 'RetraRomFiles';
const romDatabaseVersion = 2;
const romStoreName = 'romFiles';
const romCoverStoreName = 'romCovers';
let sessionRomFiles = new Map();
const romCoverObjectUrls = new Map();
let libraryRoms = [];
let archivedLibraryRoms = [];
let activeLibraryActionRomId = null;
let librarySelectedRomIds = new Set();
const renderedLibraryCards = new Map();
let pendingMultiCategoryTouched = new Set();
let pendingLaunchHistoryRomId = null;
const playHistoryStorageKey = 'retraPlayHistoryV1';
const incognitoHistoryStorageKey = 'retraIncognitoHistoryV1';
let playHistory = [];
try {
  const storedHistory = JSON.parse(localStorage.getItem(playHistoryStorageKey) || '[]');
  playHistory = Array.isArray(storedHistory) ? storedHistory : [];
} catch (error) {
  playHistory = [];
}

// v1.0.3 Library Sort/Display preferences. These are presentation-only: they
// never rename, move or rewrite ROM files and they survive app restarts.
const libraryViewStorageKey = 'retraLibraryViewV1';
const libraryViewDefaults = Object.freeze({
  sort: 'alphabetical',
  direction: 'desc',
  display: 'compact',
  itemsPerRow: 3,
  randomSeed: 1
});

function normalizeLibraryViewPrefs(value){
  const next = value && typeof value === 'object' ? value : {};
  const allowedSort = new Set(['alphabetical', 'playtime', 'lastPlayed', 'dateAdded', 'random']);
  const allowedDisplay = new Set(['compact', 'comfortable', 'coverOnly', 'list']);
  const sort = allowedSort.has(next.sort) ? next.sort : libraryViewDefaults.sort;
  const direction = next.direction === 'asc' ? 'asc' : 'desc';
  const display = allowedDisplay.has(next.display) ? next.display : libraryViewDefaults.display;
  const itemsPerRow = Math.min(6, Math.max(2, Math.round(Number(next.itemsPerRow) || libraryViewDefaults.itemsPerRow)));
  const randomSeed = Math.max(1, Math.floor(Number(next.randomSeed) || libraryViewDefaults.randomSeed));
  return { sort, direction, display, itemsPerRow, randomSeed };
}

let libraryViewPrefs = (() => {
  try {
    return normalizeLibraryViewPrefs(JSON.parse(localStorage.getItem(libraryViewStorageKey) || '{}'));
  } catch (_) {
    return { ...libraryViewDefaults };
  }
})();

function saveLibraryViewPrefs(){
  libraryViewPrefs = normalizeLibraryViewPrefs(libraryViewPrefs);
  try { localStorage.setItem(libraryViewStorageKey, JSON.stringify(libraryViewPrefs)); } catch (_) {}
}

function refreshLibraryRandomSeed(){
  libraryViewPrefs.randomSeed = Math.max(1, Date.now() % 2147483647);
  saveLibraryViewPrefs();
}

function readLibraryPlaytimeSortMap(){
  try {
    if (!(window.AndroidBridge && typeof window.AndroidBridge.getNativeStatistics === 'function')) return {};
    const raw = window.AndroidBridge.getNativeStatistics();
    const parsed = typeof raw === 'string' ? JSON.parse(raw || '{}') : raw;
    return parsed?.playtimeMsByRom && typeof parsed.playtimeMsByRom === 'object' ? parsed.playtimeMsByRom : {};
  } catch (_) {
    return {};
  }
}

function stableLibraryRandomRank(id, seed){
  const text = `${seed}|${String(id || '')}`;
  let hash = 2166136261;
  for (let i = 0; i < text.length; i += 1) {
    hash ^= text.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

let suppressedLibraryCardClickId = null;
let suppressLibraryCardClickUntil = 0;

function suppressLibraryCardClickFor(id, durationMs = 650){
  suppressedLibraryCardClickId = String(id || '');
  suppressLibraryCardClickUntil = Date.now() + Math.max(0, Number(durationMs) || 0);
}

function shouldSuppressLibraryCardClick(id){
  const sameCard = String(id || '') === suppressedLibraryCardClickId;
  const active = sameCard && Date.now() < suppressLibraryCardClickUntil;
  if (sameCard || Date.now() >= suppressLibraryCardClickUntil) {
    suppressedLibraryCardClickId = null;
    suppressLibraryCardClickUntil = 0;
  }
  return active;
}
const libraryCardLongPressMs = 520;
const libraryCardMoveTolerance = 12;

try {
  const stored = JSON.parse(localStorage.getItem(romLibraryStorageKey) || '[]');
  libraryRoms = Array.isArray(stored) ? stored : [];
} catch (error) {
  libraryRoms = [];
}

try {
  const storedArchived = JSON.parse(localStorage.getItem(archivedRomLibraryStorageKey) || '[]');
  archivedLibraryRoms = Array.isArray(storedArchived) ? storedArchived : [];
} catch (error) {
  archivedLibraryRoms = [];
}

function syncRomMetadataToNative(rom){
  if (!rom?.id || !(window.AndroidBridge && typeof window.AndroidBridge.updateRomLibraryMetadata === 'function')) return;
  try {
    const assignments = readCategoryMap('retraRomCategoryAssignments');
    const categories = Array.isArray(assignments[String(rom.id)]) ? assignments[String(rom.id)] : [];
    window.AndroidBridge.updateRomLibraryMetadata(String(rom.id), Boolean(rom.favorite), JSON.stringify(categories));
  } catch (_) {}
}

function syncRomCompletionStateToNative(){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.syncRomCompletionState === 'function')) return;
  const state = {};
  [...libraryRoms, ...archivedLibraryRoms].forEach(rom => {
    const id = String(rom?.id || '');
    if (id) state[id] = Boolean(rom.completed);
  });
  try { window.AndroidBridge.syncRomCompletionState(JSON.stringify(state)); } catch (_) {}
}

function saveLibraryRoms(){
  localStorage.setItem(romLibraryStorageKey, JSON.stringify(libraryRoms));
  libraryRoms.forEach(syncRomMetadataToNative);
  syncRomCompletionStateToNative();
}

function saveArchivedLibraryRoms(){
  localStorage.setItem(archivedRomLibraryStorageKey, JSON.stringify(archivedLibraryRoms));
  archivedLibraryRoms.forEach(syncRomMetadataToNative);
  syncRomCompletionStateToNative();
}

function hydrateLibraryFromNativeRoom(){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.getNativeLibraryRecords === 'function')) return false;
  try {
    const records = JSON.parse(window.AndroidBridge.getNativeLibraryRecords() || '[]');
    if (!Array.isArray(records) || !records.length) return false;
    const localById = new Map([...libraryRoms, ...archivedLibraryRoms].map(rom => [String(rom.id), rom]));
    const localAssignments = readCategoryMap('retraRomCategoryAssignments');
    const migrationComplete = typeof window.AndroidBridge.isLibraryMetadataRoomMigrationComplete === 'function'
      ? Boolean(window.AndroidBridge.isLibraryMetadataRoomMigrationComplete())
      : true;
    const active = [];
    const archived = [];
    const nativeHistory = [];
    const assignments = {};
    const categoryNames = new Set(readCustomCategories());

    records.forEach(record => {
      const id = String(record.id || record.romId || '');
      if (!id) return;
      const previous = localById.get(id) || {};
      const roomCategories = Array.isArray(record.categories) ? [...new Set(record.categories.filter(Boolean))] : [];
      const legacyCategories = Array.isArray(localAssignments[id]) ? [...new Set(localAssignments[id].filter(Boolean))] : [];
      const categories = migrationComplete ? roomCategories : (legacyCategories.length ? legacyCategories : roomCategories);
      const favorite = migrationComplete ? Boolean(record.favorite) : Boolean(previous.favorite || record.favorite);

      categories.forEach(name => categoryNames.add(name));
      assignments[id] = categories;
      const merged = {
        ...previous,
        id,
        title: record.title || previous.title || 'ROM',
        fileName: record.fileName || previous.fileName || '',
        system: record.system || previous.system || 'ROM',
        size: Number(record.size || previous.size || 0),
        lastModified: Number(record.lastModified || previous.lastModified || 0),
        contentHash: record.contentHash || previous.contentHash || '',
        native: true,
        favorite,
        completed: Boolean(record.completed || previous.completed),
        fileAvailable: record.fileAvailable !== false
      };
      const nativePlayedAt = Math.max(0, Number(record.lastPlayedAt) || 0);
      if (nativePlayedAt > 0) {
        nativeHistory.push({
          romId: id,
          title: merged.title,
          fileName: merged.fileName,
          system: merged.system,
          playedAt: nativePlayedAt,
          playCount: Math.max(1, Number(record.playCount) || 1)
        });
      }
      (record.archived ? archived : active).push(merged);

      if (!migrationComplete && typeof window.AndroidBridge.updateRomLibraryMetadata === 'function') {
        try { window.AndroidBridge.updateRomLibraryMetadata(id, favorite, JSON.stringify(categories)); } catch (_) {}
      }
    });

    libraryRoms = active;
    archivedLibraryRoms = archived;
    localStorage.setItem(romLibraryStorageKey, JSON.stringify(libraryRoms));
    localStorage.setItem(archivedRomLibraryStorageKey, JSON.stringify(archivedLibraryRoms));
    localStorage.setItem('retraRomCategoryAssignments', JSON.stringify(assignments));
    localStorage.setItem('retraCategories', JSON.stringify([...categoryNames]));

    // Rehydrate portable History/statistics metadata from Room/prefs. Restored
    // missing-ROM placeholders therefore still count as Started/Recent and keep
    // their History row while waiting for the matching ROM file.
    const historyById = new Map(playHistory.map(entry => [String(entry?.romId || ''), entry]));
    nativeHistory.forEach(entry => {
      const previous = historyById.get(String(entry.romId));
      if (!previous || Number(entry.playedAt) >= Number(previous.playedAt || 0)) {
        historyById.set(String(entry.romId), {
          ...previous,
          ...entry,
          playCount: Math.max(Number(previous?.playCount) || 0, Number(entry.playCount) || 0)
        });
      }
    });
    playHistory = [...historyById.values()]
      .filter(entry => entry?.romId)
      .sort((a, b) => Number(b.playedAt || 0) - Number(a.playedAt || 0))
      .slice(0, 100);
    localStorage.setItem(playHistoryStorageKey, JSON.stringify(playHistory));

    if (!migrationComplete && typeof window.AndroidBridge.markLibraryMetadataRoomMigrationComplete === 'function') {
      try { window.AndroidBridge.markLibraryMetadataRoomMigrationComplete(); } catch (_) {}
    }
    return true;
  } catch (_) {
    return false;
  }
}

function openRomDatabase(){
  return new Promise((resolve, reject) => {
    if (!('indexedDB' in window)) {
      reject(new Error('IndexedDB is unavailable'));
      return;
    }

    const request = indexedDB.open(romDatabaseName, romDatabaseVersion);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(romStoreName)) {
        db.createObjectStore(romStoreName, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(romCoverStoreName)) {
        db.createObjectStore(romCoverStoreName, { keyPath: 'id' });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('Could not open ROM storage'));
  });
}

async function storeRomFile(id, file){
  sessionRomFiles.set(id, file);
  const db = await openRomDatabase();
  await new Promise((resolve, reject) => {
    const tx = db.transaction(romStoreName, 'readwrite');
    tx.objectStore(romStoreName).put({
      id,
      file,
      name: file.name,
      size: file.size,
      type: file.type,
      lastModified: file.lastModified
    });
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error || new Error('Could not save ROM file'));
    tx.onabort = () => reject(tx.error || new Error('Could not save ROM file'));
  });
  db.close();
}

async function getStoredRomFile(id){
  if (sessionRomFiles.has(id)) return sessionRomFiles.get(id);
  try {
    const db = await openRomDatabase();
    const record = await new Promise((resolve, reject) => {
      const tx = db.transaction(romStoreName, 'readonly');
      const request = tx.objectStore(romStoreName).get(id);
      request.onsuccess = () => resolve(request.result || null);
      request.onerror = () => reject(request.error || new Error('Could not read ROM file'));
    });
    db.close();
    return record?.file || null;
  } catch (error) {
    return null;
  }
}

async function storeRomCoverBlob(id, blob, source = 'auto'){
  if (!id || !blob) return;
  const db = await openRomDatabase();
  await new Promise((resolve, reject) => {
    const tx = db.transaction(romCoverStoreName, 'readwrite');
    tx.objectStore(romCoverStoreName).put({ id, blob, source, updatedAt: Date.now() });
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error || new Error('Could not cache cover'));
    tx.onabort = () => reject(tx.error || new Error('Could not cache cover'));
  });
  db.close();
}

async function getStoredRomCoverBlob(id){
  if (!id) return null;
  try {
    const db = await openRomDatabase();
    const record = await new Promise((resolve, reject) => {
      const tx = db.transaction(romCoverStoreName, 'readonly');
      const request = tx.objectStore(romCoverStoreName).get(id);
      request.onsuccess = () => resolve(request.result || null);
      request.onerror = () => reject(request.error || new Error('Could not read cached cover'));
    });
    db.close();
    return record?.blob || null;
  } catch (error) {
    return null;
  }
}

async function deleteStoredRomCover(id){
  const existingUrl = romCoverObjectUrls.get(id);
  if (existingUrl && existingUrl.startsWith('blob:')) URL.revokeObjectURL(existingUrl);
  romCoverObjectUrls.delete(id);
  try {
    const db = await openRomDatabase();
    await new Promise((resolve, reject) => {
      const tx = db.transaction(romCoverStoreName, 'readwrite');
      tx.objectStore(romCoverStoreName).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error('Could not remove cached cover'));
      tx.onabort = () => reject(tx.error || new Error('Could not remove cached cover'));
    });
    db.close();
  } catch (error) {}
}

function setRomCoverObjectUrl(id, blob){
  const oldUrl = romCoverObjectUrls.get(id);
  if (oldUrl && oldUrl.startsWith('blob:')) URL.revokeObjectURL(oldUrl);
  const url = URL.createObjectURL(blob);
  romCoverObjectUrls.set(id, url);
  return url;
}

function getNativeRomMediaUrl(romId, kind){
  if (!romId || !(window.AndroidBridge && typeof window.AndroidBridge.getRomMediaUrl === 'function')) return '';
  try { return String(window.AndroidBridge.getRomMediaUrl(String(romId), String(kind)) || ''); }
  catch (_) { return ''; }
}

function saveNativeRomMedia(romId, kind, dataUrl){
  if (!romId || !dataUrl || !(window.AndroidBridge && typeof window.AndroidBridge.saveRomMedia === 'function')) return false;
  try { return Boolean(window.AndroidBridge.saveRomMedia(String(romId), String(kind), String(dataUrl))); }
  catch (_) { return false; }
}

/**
 * Apply a persisted/manual/automatic cover to every currently visible surface.
 *
 * Both the manual cover picker and the native automatic-artwork callback use
 * this helper. Keeping it centralized prevents a saved/downloaded cover from
 * succeeding on disk but failing to appear in the Library/detail UI.
 */
function applyCoverToRenderedRom(rom, coverUrl, card = null){
  if (!rom?.id || !coverUrl) return false;
  const id = String(rom.id);
  const url = String(coverUrl);
  const targetCard = card || renderedLibraryCards.get(id) || null;

  rom.coverCached = true;

  if (targetCard) {
    targetCard.dataset.cover = url;
    targetCard.classList.remove('no-cover');
    targetCard.querySelector('.cover-art-placeholder')?.remove();

    let image = [...targetCard.children].find(child => child.tagName === 'IMG') || null;
    if (!image) {
      image = document.createElement('img');
      image.loading = 'lazy';
      image.decoding = 'async';
      image.fetchPriority = 'low';
      targetCard.prepend(image);
    }
    image.src = url;
    image.alt = `${rom.title || 'ROM'} cover`;
  }

  if (currentRomCard && String(currentRomCard.dataset?.romId || '') === id) {
    currentRomCard.dataset.cover = url;
    detailCover.src = url;
    detailCover.alt = `${rom.title || currentRomCard.dataset?.title || 'ROM'} cover`;
    detailCover.hidden = false;
    detailCover.closest('.rom-cover')?.classList.remove('no-cover');
  }

  if (activeLibraryActionRomId && String(activeLibraryActionRomId) === id && romCardActionsCover) {
    romCardActionsCover.src = url;
    romCardActionsCover.alt = `${rom.title || 'ROM'} cover`;
    romCardActionsCover.hidden = false;
  }

  // Keep an already-open History page in sync when automatic artwork finishes
  // or a manual cover is changed from another surface.
  const historyRow = [...document.querySelectorAll('#historyList .history-item')]
    .find(item => String(item.dataset?.romId || '') === id);
  if (historyRow) {
    const coverWrap = historyRow.querySelector('.history-cover');
    if (coverWrap) {
      let historyImage = coverWrap.querySelector('img');
      if (!historyImage) {
        historyImage = document.createElement('img');
        historyImage.loading = 'lazy';
        historyImage.decoding = 'async';
        coverWrap.replaceChildren(historyImage);
      }
      historyImage.src = url;
      historyImage.alt = `${rom.title || 'ROM'} cover`;
      coverWrap.classList.remove('no-cover');
    }
  }

  // Keep Statistics -> Playtime by ROM in sync too. A manual/automatic cover
  // change should be visible immediately without leaving and reopening the page.
  const statisticsRow = [...document.querySelectorAll('#playtimeList .playtime-item')]
    .find(item => String(item.dataset?.playtimeRom || '') === id);
  if (statisticsRow) {
    const coverWrap = statisticsRow.querySelector('.playtime-cover');
    if (coverWrap) {
      let statisticsImage = coverWrap.querySelector('img');
      if (!statisticsImage) {
        statisticsImage = document.createElement('img');
        statisticsImage.loading = 'lazy';
        statisticsImage.decoding = 'async';
        statisticsImage.fetchPriority = 'low';
        coverWrap.replaceChildren(statisticsImage);
      }
      statisticsImage.src = url;
      statisticsImage.alt = `${rom.title || 'ROM'} cover`;
    }
  }

  return true;
}

async function hydrateCachedRomCover(rom, card = null){
  if (rom?.id) {
    const nativeCover = getNativeRomMediaUrl(rom.id, 'cover');
    if (nativeCover) {
      applyCoverToRenderedRom(rom, nativeCover, card);
      return;
    }
  }
  if (!rom?.id || romCoverObjectUrls.has(rom.id)) {
    if (rom?.id && romCoverObjectUrls.has(rom.id)) applyCoverToRenderedRom(rom, romCoverObjectUrls.get(rom.id), card);
    return;
  }

  // Legacy/manual data-URL covers from older versions remain usable and are
  // migrated into IndexedDB once without keeping large image data in metadata.
  if (rom.cover) {
    try {
      const legacyBlob = await (await fetch(rom.cover)).blob();
      if (legacyBlob.size) {
        await storeRomCoverBlob(rom.id, legacyBlob, rom.coverSource || 'manual');
        const url = setRomCoverObjectUrl(rom.id, legacyBlob);
        rom.coverCached = true;
        delete rom.cover;
        saveLibraryRoms();
        applyCoverToRenderedRom(rom, url, card);
        return;
      }
    } catch (error) {}
  }

  if (!rom.coverCached) return;
  const blob = await getStoredRomCoverBlob(rom.id);
  if (!blob) {
    rom.coverCached = false;
    saveLibraryRoms();
    return;
  }
  const url = setRomCoverObjectUrl(rom.id, blob);
  applyCoverToRenderedRom(rom, url, card);
}

async function deleteStoredRomFile(id){
  sessionRomFiles.delete(id);
  try {
    const db = await openRomDatabase();
    await new Promise((resolve, reject) => {
      const tx = db.transaction(romStoreName, 'readwrite');
      tx.objectStore(romStoreName).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error || new Error('Could not remove ROM file'));
      tx.onabort = () => reject(tx.error || new Error('Could not remove ROM file'));
    });
    db.close();
  } catch (error) {
    // Metadata removal should still succeed if persistent file storage is unavailable.
  }
  await deleteStoredRomCover(id);
}

function getRomById(id){
  return libraryRoms.find(rom => rom.id === id) || null;
}

function orderedLibraryRoms(){
  const prefs = normalizeLibraryViewPrefs(libraryViewPrefs);
  const direction = prefs.direction === 'asc' ? 1 : -1;
  const playtimeByRom = prefs.sort === 'playtime' ? readLibraryPlaytimeSortMap() : {};
  const lastPlayedByRom = new Map();
  if (prefs.sort === 'lastPlayed') {
    playHistory.forEach(entry => {
      const id = String(entry?.romId || '');
      const playedAt = Math.max(0, Number(entry?.playedAt) || 0);
      if (id && playedAt > (lastPlayedByRom.get(id) || 0)) lastPlayedByRom.set(id, playedAt);
    });
  }

  const rows = libraryRoms.map((rom, index) => ({ rom, index }));
  rows.sort((a, b) => {
    const aRom = a.rom || {};
    const bRom = b.rom || {};
    let compared = 0;

    if (prefs.sort === 'alphabetical') {
      compared = String(aRom.title || aRom.fileName || '').localeCompare(
        String(bRom.title || bRom.fileName || ''),
        undefined,
        { sensitivity: 'base', numeric: true }
      );
    } else if (prefs.sort === 'playtime') {
      compared = (Math.max(0, Number(playtimeByRom[String(aRom.id)]) || 0) - Math.max(0, Number(playtimeByRom[String(bRom.id)]) || 0));
    } else if (prefs.sort === 'lastPlayed') {
      compared = (lastPlayedByRom.get(String(aRom.id)) || 0) - (lastPlayedByRom.get(String(bRom.id)) || 0);
    } else if (prefs.sort === 'dateAdded') {
      compared = (Math.max(0, Number(aRom.addedAt) || 0) - Math.max(0, Number(bRom.addedAt) || 0));
    } else if (prefs.sort === 'random') {
      compared = stableLibraryRandomRank(aRom.id, prefs.randomSeed) - stableLibraryRandomRank(bRom.id, prefs.randomSeed);
    }

    if (prefs.sort !== 'random' && compared !== 0) return compared * direction;
    if (prefs.sort === 'random' && compared !== 0) return compared;

    // Deterministic tie-breaks keep the grid stable while covers/artwork hydrate.
    const titleTie = String(aRom.title || '').localeCompare(String(bRom.title || ''), undefined, { sensitivity: 'base', numeric: true });
    if (titleTie !== 0) return titleTie;
    return a.index - b.index;
  });
  return rows.map(item => item.rom);
}

function getSelectedLibraryRoms(){
  // One pass keeps bulk selection responsive even with large ROM-hack libraries.
  return libraryRoms.filter(rom => librarySelectedRomIds.has(String(rom.id)));
}

function isLibrarySelectionMode(){
  return librarySelectedRomIds.size > 0;
}

function setLibrarySelectionMoreOpen(open){
  const next = Boolean(open && isLibrarySelectionMode());
  librarySelectionMoreMenu?.classList.toggle('open', next);
  librarySelectionMoreMenu?.setAttribute('aria-hidden', next ? 'false' : 'true');
  librarySelectionMore?.setAttribute('aria-expanded', next ? 'true' : 'false');
}

function applyLibrarySelectionCardState(card, active){
  if (!card) return;
  const chosen = librarySelectedRomIds.has(String(card.dataset.romId || ''));
  card.classList.toggle('library-selected', chosen);
  card.dataset.selected = chosen ? 'true' : 'false';
  if (active) {
    card.setAttribute('aria-pressed', chosen ? 'true' : 'false');
    card.setAttribute('aria-selected', chosen ? 'true' : 'false');
  } else {
    card.removeAttribute('aria-pressed');
    card.removeAttribute('aria-selected');
  }
}

function patchLibrarySelectionCardImmediately(id, card = null){
  const key = String(id || '');
  if (!key) return;
  let target = card || renderedLibraryCards.get(key) || null;
  if (!target && libraryGrid) {
    target = [...libraryGrid.querySelectorAll('.cover-card')]
      .find(candidate => String(candidate.dataset.romId || '') === key) || null;
  }
  if (!target) return;
  // Keep the visual state synchronous with the tap. The count/favourite label
  // can update afterwards without making a selected ROM appear unselected.
  applyLibrarySelectionCardState(target, librarySelectedRomIds.size > 0);
}

function updateLibrarySelectionUi({ changedId = null, wasActive = null } = {}){
  const validIds = new Set(libraryRoms.map(rom => String(rom.id)));
  librarySelectedRomIds = new Set([...librarySelectedRomIds].filter(id => validIds.has(String(id))));
  const selected = getSelectedLibraryRoms();
  const active = selected.length > 0;

  document.body.classList.toggle('library-selection-mode', active);
  librarySelectionBar?.classList.toggle('open', active);
  librarySelectionBar?.setAttribute('aria-hidden', active ? 'false' : 'true');
  if (librarySelectionCount) librarySelectionCount.textContent = `${selected.length} selected`;

  // Once selection mode is already open, a normal tap only changes one card.
  // Avoid querying/updating every ROM in the grid on every additional tap.
  const canPatchSingleCard = changedId !== null && wasActive === true && active === true;
  if (canPatchSingleCard) {
    applyLibrarySelectionCardState(renderedLibraryCards.get(String(changedId)), true);
  } else {
    renderedLibraryCards.forEach(card => applyLibrarySelectionCardState(card, active));
  }

  const favoriteAll = librarySelectionApi.shouldFavoriteAll(selected);
  if (librarySelectionFavoriteLabel) librarySelectionFavoriteLabel.textContent = favoriteAll ? 'Favourite' : 'Unfavourite';
  librarySelectionFavorite?.classList.toggle('active', !favoriteAll && active);
  librarySelectionFavorite?.setAttribute('aria-label', favoriteAll ? 'Add selected ROMs to favourites' : 'Remove selected ROMs from favourites');

  if (!active) setLibrarySelectionMoreOpen(false);
}

function exitLibrarySelection(){
  librarySelectedRomIds.clear();
  pendingMultiCategoryTouched.clear();
  setLibrarySelectionMoreOpen(false);
  multiCategoryModal?.classList.remove('open');
  multiCategoryModal?.setAttribute('aria-hidden', 'true');
  updateLibrarySelectionUi();
}

function enterLibrarySelection(id){
  const key = String(id || '');
  if (!key || !getRomById(key)) return;
  if (!isLibrarySelectionMode()) librarySelectedRomIds.clear();
  librarySelectedRomIds.add(key);
  closeLibraryRomActions();
  updateLibrarySelectionUi();
}

function toggleLibraryRomSelection(id, card = null){
  const key = String(id || '');
  if (!key) return;
  const wasActive = isLibrarySelectionMode();
  if (librarySelectedRomIds.has(key)) librarySelectedRomIds.delete(key);
  else if (getRomById(key)) librarySelectedRomIds.add(key);

  // Paint this card in the same event turn as the tap. This avoids the brief
  // unhighlighted state that could occur while other Library work was queued.
  patchLibrarySelectionCardImmediately(key, card);
  updateLibrarySelectionUi({ changedId: key, wasActive });
}

function closeLibraryRomActions(){
  romCardActionsModal?.classList.remove('open');
  romCardActionsModal?.setAttribute('aria-hidden', 'true');
  activeLibraryActionRomId = null;
}

function openLibraryRomActions(rom, card){
  if (!rom || !romCardActionsModal) return;
  activeLibraryActionRomId = rom.id;
  const cover = card?.querySelector('img')?.src || rom.cover || '';
  if (romCardActionsCover) {
    romCardActionsCover.src = cover;
    romCardActionsCover.alt = cover ? `${rom.title} cover` : '';
    romCardActionsCover.hidden = !cover;
  }
  if (romCardActionsTitle) romCardActionsTitle.textContent = rom.title || 'ROM';
  if (romFavoriteActionLabel) romFavoriteActionLabel.textContent = rom.favorite ? 'Remove from Favourites' : 'Add to Favourites';
  romFavoriteAction?.classList.toggle('active', Boolean(rom.favorite));
  romCardActionsModal.classList.add('open');
  romCardActionsModal.setAttribute('aria-hidden', 'false');
}

async function removeRomFromLibrary(id){
  const rom = getRomById(id);
  if (!rom) return;

  // Remove means archive. Do not delete IndexedDB ROM/cover data, category
  // assignments, favourites, saves, states, cheats, layouts or statistics.
  libraryRoms = libraryRoms.filter(item => item.id !== id);
  const archived = { ...rom, archived: true, archivedAt: Date.now() };
  const existingArchived = archivedLibraryRoms.findIndex(item => item.id === id);
  if (existingArchived >= 0) archivedLibraryRoms[existingArchived] = archived;
  else archivedLibraryRoms.unshift(archived);
  saveLibraryRoms();
  saveArchivedLibraryRoms();

  try {
    if (window.AndroidBridge && typeof window.AndroidBridge.archiveRom === 'function') {
      window.AndroidBridge.archiveRom(String(id));
    }
  } catch (error) {}

  if (currentRomCard?.dataset.romId === id) currentRomCard = null;
  renderLibraryFromStorage();
  showToast('Removed from Library • game data kept');
}

async function deleteRomGameData(id){
  const rom = getRomById(id) || archivedLibraryRoms.find(item => item.id === id);
  if (!rom) return;
  let deleted = false;
  try {
    deleted = Boolean(window.AndroidBridge &&
      typeof window.AndroidBridge.deleteGameData === 'function' &&
      window.AndroidBridge.deleteGameData(String(id)));
  } catch (error) {
    deleted = false;
  }
  if (deleted) {
    playHistory = playHistory.filter(entry => String(entry?.romId || '') !== String(id));
    localStorage.setItem(playHistoryStorageKey, JSON.stringify(playHistory));
    if (rom && typeof rom === 'object') {
      rom.playtimeMs = 0;
      rom.lastPlayed = null;
      rom.lastPlayedAt = null;
    }
    saveLibraryRoms();
    renderHistory();
    updateStatistics();
    showToast(`Game data deleted for ${rom.title || 'ROM'}`);
  } else showToast('Could not delete game data');
}

function removeSelectedRomsFromLibrary(){
  const selected = getSelectedLibraryRoms();
  if (!selected.length) return;
  const selectedIds = new Set(selected.map(rom => String(rom.id)));

  selected.forEach(rom => {
    const archived = { ...rom, archived: true, archivedAt: Date.now() };
    const index = archivedLibraryRoms.findIndex(item => String(item.id) === String(rom.id));
    if (index >= 0) archivedLibraryRoms[index] = archived;
    else archivedLibraryRoms.unshift(archived);
    try {
      window.AndroidBridge?.archiveRom?.(String(rom.id));
    } catch (error) {}
  });

  libraryRoms = libraryRoms.filter(rom => !selectedIds.has(String(rom.id)));
  if (currentRomCard && selectedIds.has(String(currentRomCard.dataset.romId || ''))) currentRomCard = null;
  saveLibraryRoms();
  saveArchivedLibraryRoms();
  librarySelectedRomIds.clear();
  renderLibraryFromStorage();
  showToast(`${selected.length} ROM${selected.length === 1 ? '' : 's'} removed • game data kept`);
}

function bindRomCardLongPress(article, rom){
  let pressTimer = null;
  let startX = 0;
  let startY = 0;
  let pointerId = null;
  let longPressTriggered = false;

  const clearPressTimer = () => {
    if (pressTimer) clearTimeout(pressTimer);
    pressTimer = null;
  };

  article.addEventListener('pointerdown', event => {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    if (isLibrarySelectionMode()) return;
    clearPressTimer();
    pointerId = event.pointerId;
    startX = event.clientX;
    startY = event.clientY;
    longPressTriggered = false;
    article.classList.add('holding');

    pressTimer = setTimeout(() => {
      longPressTriggered = true;
      suppressLibraryCardClickFor(rom.id);
      article.classList.remove('holding');
      article.classList.add('long-pressed');
      enterLibrarySelection(rom.id);
      if (navigator.vibrate) navigator.vibrate(10);
    }, libraryCardLongPressMs);
  });

  article.addEventListener('pointermove', event => {
    if (pointerId !== event.pointerId || !pressTimer) return;
    if (Math.hypot(event.clientX - startX, event.clientY - startY) > libraryCardMoveTolerance) {
      clearPressTimer();
      article.classList.remove('holding');
    }
  });

  ['pointerup', 'pointercancel', 'pointerleave'].forEach(eventName => {
    article.addEventListener(eventName, event => {
      if (pointerId !== null && event.pointerId !== undefined && event.pointerId !== pointerId) return;
      clearPressTimer();
      article.classList.remove('holding');
      if (longPressTriggered) {
        suppressLibraryCardClickFor(rom.id);
        setTimeout(() => article.classList.remove('long-pressed'), 120);
      }
      pointerId = null;
    });
  });

  article.addEventListener('contextmenu', event => {
    event.preventDefault();
    clearPressTimer();
    article.classList.remove('holding');
    suppressLibraryCardClickFor(rom.id);
    enterLibrarySelection(rom.id);
  });
}

function createRomId(){
  if (window.crypto?.randomUUID) return crypto.randomUUID();
  return `rom-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function getFileExtension(fileName){
  const match = String(fileName || '').toLowerCase().match(/\.([a-z0-9]+)$/);
  return match ? match[1] : '';
}

function inferRomSystem(fileName){
  const ext = getFileExtension(fileName);
  if (ext === 'gba') return 'GBA';
  if (ext === 'gbc') return 'GBC';
  if (ext === 'gb') return 'GB';
  if (ext === 'mgba') return 'mGBA';
  if (['ips', 'ups', 'bps'].includes(ext)) return 'PATCH';
  if (ext === 'zip') return 'ARCHIVE';
  return 'ROM';
}

function titleFromFileName(fileName){
  const withoutExt = String(fileName || 'ROM').replace(/\.[^.]+$/, '');
  return withoutExt
    .replace(/[._-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim() || 'ROM';
}

function formatRomFileSize(bytes){
  const value = Number(bytes) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function escapeXmlText(value){
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}



// Unified Retra menu haptics -------------------------------------------------
// Native gameplay controls already vibrate directly in Kotlin. The WebView UI
// forwards only real interactive taps so scrolling never causes vibration.
(() => {
  let lastUiHapticAt = 0;
  const selector = [
    'button:not([disabled])',
    'a[href]',
    'input[type="checkbox"]:not([disabled])',
    'input[type="radio"]:not([disabled])',
    'input[type="range"]:not([disabled])',
    'select:not([disabled])',
    '[role="button"]',
    '[data-open-page]',
    '[data-action]',
    '.tappable',
    '.switch',
    '.backup-check-row'
  ].join(',');

  document.addEventListener('pointerdown', event => {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    const target = event.target instanceof Element ? event.target.closest(selector) : null;
    if (!target || target.matches('[disabled]') || target.closest('.disabled-row,[aria-disabled="true"]')) return;

    const now = performance.now();
    if (now - lastUiHapticAt < 24) return;
    lastUiHapticAt = now;

    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.performUiHaptic === 'function') {
        window.AndroidBridge.performUiHaptic();
      }
    } catch (_) {}
  }, { capture: true, passive: true });
})();
