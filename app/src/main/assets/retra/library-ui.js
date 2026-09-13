const automaticArtworkRequested = new Set();

function fallbackArtworkPalette(rom){
  const seed = `${rom?.id || ''}|${rom?.title || ''}`;
  let hash = 2166136261;
  for (let i = 0; i < seed.length; i += 1) {
    hash ^= seed.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  const hue = Math.abs(hash >>> 0) % 360;
  return {
    a: `hsl(${hue} 48% 40%)`,
    b: `hsl(${(hue + 38) % 360} 58% 18%)`
  };
}

function applyFallbackArtwork(card, rom){
  if (!card || !rom || !card.classList.contains('no-cover')) return;
  const palette = fallbackArtworkPalette(rom);
  card.style.setProperty('--cover-fallback-a', palette.a);
  card.style.setProperty('--cover-fallback-b', palette.b);
}

function queueNativeAutomaticArtwork(rom, force = false){
  if (!rom?.id || rom.coverSource === 'manual') return false;
  if (!navigator.onLine) return false;
  if (!(window.AndroidBridge && typeof window.AndroidBridge.queueRomArtwork === 'function')) return false;
  const key = String(rom.id);
  if (!force && automaticArtworkRequested.has(key)) return true;
  try {
    const accepted = Boolean(window.AndroidBridge.queueRomArtwork(key, Boolean(force)));
    if (accepted) {
      automaticArtworkRequested.add(key);
      window.setTimeout(() => automaticArtworkRequested.delete(key), 30000);
    }
    return accepted;
  } catch (_) {
    return false;
  }
}

async function lookupAutomaticRomCover(rom, force = false){
  return queueNativeAutomaticArtwork(rom, force);
}

const automaticArtworkObserver = typeof IntersectionObserver === 'function'
  ? new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        automaticArtworkObserver.unobserve(entry.target);
        const rom = getRomById(entry.target.dataset.romId || '');
        if (rom) queueNativeAutomaticArtwork(rom, false);
      });
    }, { root: null, rootMargin: '360px 0px', threshold: 0.01 })
  : null;

function observeAutomaticArtwork(card, rom){
  if (!card || !rom?.id || rom.coverSource === 'manual') return;
  if (automaticArtworkObserver) automaticArtworkObserver.observe(card);
  else scheduleUiBackgroundTask(() => queueNativeAutomaticArtwork(rom, false), 700);
}

function scheduleAutomaticCoverLookups(force = false){
  if (!navigator.onLine) return;
  const missing = libraryRoms.filter(rom => {
    if (rom.coverSource === 'manual') return false;
    const card = renderedLibraryCards.get(String(rom.id));
    return !card || card.classList.contains('no-cover');
  });
  if (force) {
    // Force retries remain serialized by the native artwork worker. Staggering
    // bridge calls keeps Refresh Library instant even with a large collection.
    missing.forEach((rom, index) => {
      window.setTimeout(() => queueNativeAutomaticArtwork(rom, true), index * 90);
    });
    return;
  }
  missing.forEach(rom => {
    const card = renderedLibraryCards.get(String(rom.id));
    if (card) observeAutomaticArtwork(card, rom);
  });
}

window.retraNativeArtworkChanged = function(romId, state){
  const id = String(romId || '');
  const rom = getRomById(id);
  if (!rom) return;
  automaticArtworkRequested.delete(id);

  const card = renderedLibraryCards.get(id) || null;
  const coverUrl = getNativeRomMediaUrl(id, 'cover');
  const backgroundUrl = getNativeRomMediaUrl(id, 'background');

  if (coverUrl) {
    rom.coverCached = true;
    if (rom.coverSource !== 'manual') rom.coverSource = 'auto';
    applyCoverToRenderedRom(rom, coverUrl, card);
  } else if (card) {
    card.classList.add('no-cover');
    applyFallbackArtwork(card, rom);
  }

  if (card && backgroundUrl) card.dataset.bg = backgroundUrl;
  if (currentRomCard?.dataset.romId === id && backgroundUrl) {
    currentRomCard.dataset.bg = backgroundUrl;
    romHero.style.setProperty('--rom-hero-image', `url("${backgroundUrl}")`);
  }

  if (String(state || '') === 'NOT_FOUND' && card) applyFallbackArtwork(card, rom);
  try { saveLibraryRoms(); } catch (_) {}
};

window.addEventListener('online', () => {
  automaticArtworkRequested.clear();
  scheduleAutomaticCoverLookups(false);
});

function createRomCard(rom){
  const article = document.createElement('article');
  article.className = 'cover-card';
  article.tabIndex = 0;
  article.setAttribute('role', 'button');
  article.setAttribute('aria-label', `${rom.title || 'ROM'}, ${rom.system || 'ROM'}`);
  article.dataset.romId = rom.id;
  renderedLibraryCards.set(String(rom.id), article);
  article.dataset.title = rom.title;
  article.dataset.fileName = rom.fileName;
  article.dataset.system = rom.system;
  article.dataset.author = 'Local file';
  article.dataset.studio = rom.system === 'PATCH' ? 'ROM patch file' : `${rom.system} ROM`;
  const fileAvailable = rom.fileAvailable !== false;
  article.dataset.fileAvailable = fileAvailable ? 'true' : 'false';
  article.dataset.status = !fileAvailable ? 'ROM file required' : (rom.system === 'PATCH' ? 'Patch file' : 'Ready to Play');
  article.dataset.source = !fileAvailable ? 'Restored Library' : 'Local Library';
  article.dataset.description = `${rom.fileName} • ${formatRomFileSize(rom.size)}`;
  article.dataset.entries = '';

  let assignments = {};
  try {
    assignments = JSON.parse(localStorage.getItem('retraRomCategoryAssignments') || '{}');
  } catch (error) {}
  article.dataset.category = Array.isArray(assignments[rom.id]) ? assignments[rom.id].join(',') : '';

  const cover = romCoverObjectUrls.get(rom.id) || rom.cover || '';
  article.dataset.cover = cover;

  let img = null;
  if (cover) {
    img = document.createElement('img');
    img.src = cover;
    img.alt = `${rom.title} cover`;
    img.loading = 'lazy';
    img.decoding = 'async';
    img.fetchPriority = 'low';
  } else {
    article.classList.add('no-cover');
  }

  const badge = document.createElement('span');
  badge.className = 'top-badge';
  badge.textContent = rom.system;

  const info = document.createElement('div');
  info.className = 'cover-info';
  const title = document.createElement('strong');
  title.textContent = rom.title;
  info.appendChild(title);

  if (rom.favorite) {
    const favourite = document.createElement('span');
    favourite.className = 'favourite-heart';
    favourite.setAttribute('aria-label', 'Favourite');
    favourite.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z"></path></svg>';
    article.appendChild(favourite);
  }

  const selectionMark = document.createElement('span');
  selectionMark.className = 'library-selection-check';
  selectionMark.setAttribute('aria-hidden', 'true');
  selectionMark.innerHTML = '<svg viewBox="0 0 24 24"><path d="m6.5 12.5 3.5 3.5 7.5-8"></path></svg>';
  article.appendChild(selectionMark);
  // A card created while multi-select is active must immediately reflect the
  // Set, even if a background artwork/update causes the Library to re-render.
  applyLibrarySelectionCardState(article, isLibrarySelectionMode());

  if (img) article.appendChild(img);
  else {
    const placeholder = document.createElement('div');
    placeholder.className = 'cover-art-placeholder';
    placeholder.setAttribute('aria-hidden', 'true');
    article.appendChild(placeholder);
  }
  article.append(badge, info);
  bindRomCardLongPress(article, rom);
  article.addEventListener('click', event => {
    if (shouldSuppressLibraryCardClick(rom.id)) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    if (isLibrarySelectionMode()) {
      event.preventDefault();
      event.stopPropagation();
      toggleLibraryRomSelection(rom.id, article);
      return;
    }
    openRomDetail(article);
  });
  article.addEventListener('keydown', event => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    if (isLibrarySelectionMode()) toggleLibraryRomSelection(rom.id, article);
    else openRomDetail(article);
  });
  if (!cover) {
    applyFallbackArtwork(article, rom);
    observeAutomaticArtwork(article, rom);
  }
  if ((!cover && rom.coverCached) || (rom.cover && String(rom.cover).startsWith('data:'))) {
    window.setTimeout(() => hydrateCachedRomCover(rom, article), 0);
  }
  return article;
}

function setStatisticValue(id, value){
  const el = document.getElementById(id);
  if (el) el.textContent = String(value);
}

function updateLibraryStats(){
  setStatisticValue('statLibraryCount', libraryRoms.length);
  setStatisticValue('statGbaCount', libraryRoms.filter(rom => rom.system === 'GBA').length);
  setStatisticValue('statGbcCount', libraryRoms.filter(rom => rom.system === 'GBC').length);
  setStatisticValue('statGbCount', libraryRoms.filter(rom => rom.system === 'GB').length);
}

function formatPlaytimeDuration(milliseconds){
  const ms = Math.max(0, Number(milliseconds) || 0);
  if (ms <= 0) return '0m';

  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${Math.max(1, seconds)}s`;

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;

  const hours = Math.floor(minutes / 60);
  const remainderMinutes = minutes % 60;
  if (hours < 100) return remainderMinutes ? `${hours}h ${remainderMinutes}m` : `${hours}h`;

  const days = Math.floor(hours / 24);
  const remainderHours = hours % 24;
  return remainderHours ? `${days}d ${remainderHours}h` : `${days}d`;
}

function getNativeStatistics(){
  try {
    if (!(window.AndroidBridge && typeof window.AndroidBridge.getNativeStatistics === 'function')) return {};
    const raw = window.AndroidBridge.getNativeStatistics();
    if (!raw) return {};
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch (error) {
    return {};
  }
}

async function hydratePlaytimeCover(row, rom){
  if (!row || !rom?.id || !rom.coverCached) return;
  try {
    const blob = await getStoredRomCoverBlob(rom.id);
    if (!blob || !row.isConnected) return;
    const cover = setRomCoverObjectUrl(rom.id, blob);
    if (!cover) return;
    const media = row.querySelector('.playtime-cover');
    if (!media) return;
    const image = document.createElement('img');
    image.loading = 'lazy';
    image.decoding = 'async';
    image.fetchPriority = 'low';
    image.src = cover;
    image.alt = `${rom.title || 'ROM'} cover`;
    media.replaceChildren(image);
  } catch (error) {}
}

function renderPlaytimeByRom(playtimeByRom){
  const list = document.getElementById('playtimeList');
  if (!list) return;

  const entries = libraryRoms
    .map(rom => ({
      rom,
      millis: Math.max(0, Number(playtimeByRom?.[String(rom.id)]) || 0)
    }))
    .filter(entry => entry.millis > 0)
    .sort((a, b) => b.millis - a.millis);

  list.innerHTML = '';

  if (!entries.length) {
    const empty = document.createElement('div');
    empty.className = 'stats-empty-message';
    empty.id = 'playtimeEmpty';
    empty.textContent = 'No playtime data yet.';
    list.appendChild(empty);
    return;
  }

  entries.forEach(({ rom, millis }) => {
    const row = document.createElement('article');
    row.className = 'playtime-item';
    row.dataset.playtimeRom = String(rom.id || '');

    const left = document.createElement('div');
    left.className = 'playtime-left';

    const coverWrap = document.createElement('div');
    coverWrap.className = 'playtime-cover';
    const cover = romCoverObjectUrls.get(rom.id) || rom.cover || '';
    if (cover) {
      const image = document.createElement('img');
      image.loading = 'lazy';
      image.decoding = 'async';
      image.fetchPriority = 'low';
      image.src = cover;
      image.alt = `${rom.title || 'ROM'} cover`;
      coverWrap.appendChild(image);
    } else {
      const placeholder = document.createElement('div');
      placeholder.className = 'playtime-cover-placeholder';
      placeholder.textContent = String(rom.title || 'R').trim().charAt(0).toUpperCase() || 'R';
      coverWrap.appendChild(placeholder);
    }

    const copy = document.createElement('div');
    copy.className = 'playtime-copy';
    const title = document.createElement('strong');
    title.textContent = rom.title || 'ROM';
    const meta = document.createElement('span');
    const history = playHistory.find(entry => String(entry.romId) === String(rom.id));
    const plays = Math.max(0, Number(history?.playCount) || 0);
    meta.textContent = `${rom.system || 'ROM'}${plays ? ` • ${plays} play${plays === 1 ? '' : 's'}` : ''}`;
    copy.append(title, meta);

    const time = document.createElement('div');
    time.className = 'playtime-time';
    time.textContent = formatPlaytimeDuration(millis);

    left.append(coverWrap, copy);
    row.append(left, time);
    list.appendChild(row);

    if (!cover && rom.coverCached) hydratePlaytimeCover(row, rom);
  });
}

function updateStatistics(){
  updateLibraryStats();

  const nativeStats = getNativeStatistics();
  const playtimeByRom = nativeStats.playtimeMsByRom && typeof nativeStats.playtimeMsByRom === 'object'
    ? nativeStats.playtimeMsByRom
    : {};

  const totalPlaytime = libraryRoms.reduce((total, rom) => {
    return total + Math.max(0, Number(playtimeByRom?.[String(rom.id)]) || 0);
  }, 0);

  const startedIds = new Set();
  playHistory.forEach(entry => {
    if (entry?.romId) startedIds.add(String(entry.romId));
  });
  Object.entries(playtimeByRom).forEach(([romId, millis]) => {
    if ((Number(millis) || 0) > 0) startedIds.add(String(romId));
  });

  const currentLibraryIds = new Set(libraryRoms.map(rom => String(rom.id)));
  const startedCount = [...startedIds].filter(id => currentLibraryIds.has(id)).length;
  const favoriteCount = libraryRoms.filter(rom => Boolean(rom.favorite)).length;
  const completedCount = libraryRoms.filter(rom => Boolean(rom.completed)).length;
  const recentCutoff = Date.now() - (30 * 24 * 60 * 60 * 1000);
  const recentCount = new Set(
    playHistory
      .filter(entry => Number(entry?.playedAt || 0) >= recentCutoff && currentLibraryIds.has(String(entry?.romId || '')))
      .map(entry => String(entry.romId))
  ).size;

  setStatisticValue('statTotalPlaytime', formatPlaytimeDuration(totalPlaytime));
  setStatisticValue('statCompletedGames', completedCount);
  setStatisticValue('statStartedCount', startedCount);
  setStatisticValue('statFavoritesCount', favoriteCount);
  setStatisticValue('statRecentCount', recentCount);
  setStatisticValue('statSaveStateCount', Math.max(0, Number(nativeStats.saveStates) || 0));
  setStatisticValue('statBatterySaveCount', Math.max(0, Number(nativeStats.batterySaves) || 0));
  setStatisticValue('statBackupCount', Math.max(0, Number(nativeStats.backups) || 0));

  renderPlaytimeByRom(playtimeByRom);
}

let libraryRenderInitialized = false;
let lastLibraryRenderSignature = '';

function getLibraryRenderSignature(){
  const assignments = readCategoryMap('retraRomCategoryAssignments');
  return JSON.stringify(orderedLibraryRoms().map(rom => [
    String(rom.id || ''),
    String(rom.title || ''),
    String(rom.fileName || ''),
    String(rom.system || ''),
    Number(rom.size) || 0,
    Boolean(rom.favorite),
    Boolean(rom.fileAvailable !== false),
    String(romCoverObjectUrls.get(rom.id) || rom.cover || ''),
    Boolean(rom.coverCached),
    Array.isArray(assignments[String(rom.id)]) ? assignments[String(rom.id)] : []
  ]));
}

function renderLibraryFromStorage({ force = false } = {}){
  if (!libraryGrid) return false;

  // Chromium can skip painting off-screen card internals for large libraries
  // while preserving each card's grid geometry and hit target. Small libraries
  // keep the normal path to avoid unnecessary containment overhead.
  libraryGrid.classList.toggle('large-library', libraryRoms.length >= 36);

  const signature = getLibraryRenderSignature();
  if (!force && libraryRenderInitialized && signature === lastLibraryRenderSignature) {
    // The cached DOM is already current. Keep image elements, decoded bitmaps,
    // focus state and card listeners instead of tearing the Home grid down.
    updateLibraryStats();
    filterCards();
    updateLibrarySelectionUi();
    return false;
  }

  const fragment = document.createDocumentFragment();
  renderedLibraryCards.clear();
  orderedLibraryRoms().forEach(rom => fragment.appendChild(createRomCard(rom)));
  libraryGrid.replaceChildren(fragment);
  lastLibraryRenderSignature = signature;
  libraryRenderInitialized = true;

  updateLibraryStats();
  filterCards();
  updateLibrarySelectionUi();
  scheduleUiBackgroundTask(() => scheduleAutomaticCoverLookups(false), 900);
  return true;
}

async function importRomFiles(fileList){
  const files = [...(fileList || [])];
  if (!files.length) return;

  const allowed = new Set(['gba', 'gbc', 'gb', 'mgba', 'zip', 'ips', 'ups', 'bps']);
  let added = 0;
  let duplicates = 0;
  let unsupported = 0;
  let persistenceWarning = false;

  for (const file of files) {
    const ext = getFileExtension(file.name);
    if (!allowed.has(ext)) {
      unsupported++;
      continue;
    }

    const duplicate = libraryRoms.some(rom =>
      rom.fileName === file.name &&
      Number(rom.size) === Number(file.size) &&
      Number(rom.lastModified) === Number(file.lastModified)
    );
    if (duplicate) {
      duplicates++;
      continue;
    }

    const id = createRomId();
    try {
      await storeRomFile(id, file);
    } catch (error) {
      sessionRomFiles.set(id, file);
      persistenceWarning = true;
    }

    const system = inferRomSystem(file.name);
    libraryRoms.unshift({
      id,
      title: titleFromFileName(file.name),
      fileName: file.name,
      size: file.size,
      lastModified: file.lastModified,
      system,
      addedAt: Date.now()
    });
    applyDefaultCategoriesToRomKey(id);
    added++;
  }

  if (added) {
    saveLibraryRoms();
    renderLibraryFromStorage();
  }

  if (persistenceWarning && added) showToast('ROM storage is unavailable; the imported game is available for this session only');
  else if (!added && duplicates) showToast('That ROM is already in your Library');
  else if (!added && unsupported) showToast('Unsupported ROM file type');
  // Normal successful imports are intentionally silent; the new Library card is the feedback.
}

function setMainPage(pageId){
  const previousPageId = activePageId();

  // Re-tapping the already active tab should be a no-op. In particular, do not
  // rebuild Home or rerun storage work just because its nav button was tapped.
  if (previousPageId === pageId && mainTabPageIds.has(pageId)) {
    restorePageUiState(pageId);
    runActiveSearch();
    return;
  }

  capturePageUiState(previousPageId);

  if (currentOrientationPage === 'screenSizePage' && pageId !== 'screenSizePage') {
    if (typeof commitScreenEditorState === 'function') commitScreenEditorState();
    if (window.AndroidBridge && typeof window.AndroidBridge.setScreenEditorActive === 'function') {
      window.AndroidBridge.setScreenEditorActive(false);
    }
  }

  pages.forEach(p => p.classList.remove('active'));
  navItems.forEach(n => n.classList.remove('active'));
  clearActiveHeaders();
  document.body.classList.remove('subpage-open', 'more-headerless', 'backup-create-open');
  updateRomDetailState(pageId);
  updateFloatingAddRomButton(pageId);
  updateShellOrientation(pageId);

  const nextPage = document.getElementById(pageId);
  if (!nextPage) return;
  nextPage.classList.add('active');
  window.RetraMotion?.pageEnter?.(nextPage, 'tab');
  document.querySelector(`.nav-item[data-page="${pageId}"]`)?.classList.add('active');

  if (pageId !== 'morePage') {
    headers[pageId]?.classList.add('active-header');
  } else {
    document.body.classList.add('more-headerless');
  }

  if (pageId === 'libraryPage' || pageId === 'historyPage') {
    updateSearchContext(pageId);
    // History may update while another page is open, but renderHistory itself
    // is signature-cached. Home never queries Room or rescans ROMs here.
    if (pageId === 'historyPage') renderHistory();
  }

  restorePageUiState(pageId);
  runActiveSearch();
  if (pageId === 'historyPage') filterHistory();
}

function openSubPage(pageId){
  const previousPageId = activePageId();
  capturePageUiState(previousPageId);
  const leavingScreenEditor = currentOrientationPage === 'screenSizePage' && pageId !== 'screenSizePage';
  const enteringScreenEditor = pageId === 'screenSizePage' && currentOrientationPage !== 'screenSizePage';

  // Screen Editor is an auto-save editor. Commit both the controller positions
  // and emulator frame as one native transaction BEFORE the page is closed.
  if (leavingScreenEditor) {
    commitScreenEditorState();
    if (window.AndroidBridge && typeof window.AndroidBridge.setScreenEditorActive === 'function') {
      window.AndroidBridge.setScreenEditorActive(false);
    }
  }

  if (enteringScreenEditor && window.AndroidBridge && typeof window.AndroidBridge.setScreenEditorActive === 'function') {
    // Use the same full-screen coordinate canvas as actual gameplay.
    window.AndroidBridge.setScreenEditorActive(true);
  }

  pages.forEach(p => p.classList.remove('active'));
  clearActiveHeaders();
  document.body.classList.remove('more-headerless');
  document.body.classList.add('subpage-open');
  document.body.classList.toggle('backup-create-open', pageId === 'createBackupPage');
  updateRomDetailState(pageId);
  updateFloatingAddRomButton(pageId);
  updateShellOrientation(pageId);
  const nextPage = document.getElementById(pageId);
  if (!nextPage) return;
  nextPage.classList.add('active');
  window.RetraMotion?.pageEnter?.(nextPage, 'forward');
  searchPanel?.classList.remove('open');
  if (appMain) appMain.scrollTop = Math.max(0, Number(pageUiState.get(pageId)?.scrollTop) || 0);

  if (pageId === 'statisticsPage') {
    updateStatistics();
  }

  if (pageId === 'screenSizePage') {
    requestAnimationFrame(() => {
      const orientation = currentEditorOrientation();
      if (typeof hydrateEditorOrientationFromNative === 'function') {
        hydrateEditorOrientationFromNative(orientation);
      }
      // Hiding Android system bars changes the WebView size. Wait for the
      // full-screen editor canvas to settle before restoring exact coordinates.
      if (typeof scheduleScreenEditorOrientationSync === 'function') {
        scheduleScreenEditorOrientationSync(140);
      } else {
        if (typeof applyScreenSizePreview === 'function') applyScreenSizePreview();
        if (typeof applyPreviewLayoutControls === 'function') applyPreviewLayoutControls();
      }
    });
  }
}

let suppressRecentSaveClickUntil = 0;
const recentSaveLongPressMs = 560;
const recentSaveMoveTolerance = 12;
const recentSaveActionsModal = document.getElementById('recentSaveActionsModal');
const recentSaveActionsTitle = document.getElementById('recentSaveActionsTitle');
const recentSaveRenameAction = document.getElementById('recentSaveRenameAction');
const recentSaveDeleteAction = document.getElementById('recentSaveDeleteAction');
const closeRecentSaveActions = document.getElementById('closeRecentSaveActions');
const recentSaveRenameModal = document.getElementById('recentSaveRenameModal');
const recentSaveNameInput = document.getElementById('recentSaveNameInput');
const cancelRecentSaveRename = document.getElementById('cancelRecentSaveRename');
const saveRecentSaveRename = document.getElementById('saveRecentSaveRename');
let pendingRecentSaveCard = null;
let pendingRecentSave = null;

function getNativeRomSaveStates(romId){
  if (!(window.AndroidBridge && typeof window.AndroidBridge.getRomSaveStates === 'function')) return null;
  try {
    const raw = window.AndroidBridge.getRomSaveStates(String(romId || ''));
    const parsed = typeof raw === 'string' ? JSON.parse(raw || '[]') : raw;
    return Array.isArray(parsed) ? parsed : [];
  } catch (error) {
    return [];
  }
}

function formatRecentSaveTimestamp(timestamp){
  const value = Number(timestamp) || 0;
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  try {
    const dateText = new Intl.DateTimeFormat(undefined, {
      day: '2-digit', month: 'short', year: 'numeric'
    }).format(date);
    const timeText = new Intl.DateTimeFormat(undefined, {
      hour: 'numeric', minute: '2-digit'
    }).format(date);
    return `${dateText} • ${timeText}`;
  } catch (error) {
    return date.toLocaleString();
  }
}

function closeRecentSaveActionsModal(){
  recentSaveActionsModal?.classList.remove('open');
  recentSaveActionsModal?.setAttribute('aria-hidden', 'true');
}

function openRecentSaveActions(card, save){
  const slot = Number(save?.slot);
  if (!Number.isInteger(slot) || slot < 0 || slot > 10) return;
  pendingRecentSaveCard = card;
  pendingRecentSave = save;
  const label = String(save?.label || (slot === 0 ? 'Quick' : `Slot ${slot}`));
  if (recentSaveActionsTitle) recentSaveActionsTitle.textContent = label;
  if (recentSaveRenameAction) recentSaveRenameAction.hidden = slot === 0;
  recentSaveActionsModal?.setAttribute('aria-hidden', 'false');
  recentSaveActionsModal?.classList.add('open');
}

function closeRecentSaveRenameModal(){
  recentSaveRenameModal?.classList.remove('open');
}

function openRecentSaveRenameModal(){
  const card = pendingRecentSaveCard;
  const save = pendingRecentSave;
  const slot = Number(save?.slot);
  if (!card || !save || !Number.isInteger(slot)) return;
  if (slot === 0) {
    closeRecentSaveActionsModal();
    showToast('Quick Save cannot be renamed');
    return;
  }
  const currentLabel = String(save?.label || `Slot ${slot}`);
  closeRecentSaveActionsModal();
  if (recentSaveNameInput) {
    recentSaveNameInput.value = currentLabel;
    setTimeout(() => {
      recentSaveNameInput.focus();
      recentSaveNameInput.select();
    }, 80);
  }
  recentSaveRenameModal?.classList.add('open');
}

function commitRecentSaveRename(){
  const card = pendingRecentSaveCard;
  const save = pendingRecentSave;
  const romId = String(card?.dataset?.romId || '');
  const slot = Number(save?.slot);
  const name = String(recentSaveNameInput?.value || '').trim().replace(/\s+/g, ' ').slice(0, 32);

  if (!romId || !Number.isInteger(slot) || slot < 1 || slot > 10) {
    showToast(slot === 0 ? 'Quick Save cannot be renamed' : 'Save state is unavailable');
    return;
  }
  if (!name) {
    showToast('Enter a save state name');
    recentSaveNameInput?.focus();
    return;
  }
  if (!(window.AndroidBridge && typeof window.AndroidBridge.renameRomSaveState === 'function')) {
    showToast('Save-state renaming is only available in the Android build');
    return;
  }

  let renamed = false;
  try { renamed = Boolean(window.AndroidBridge.renameRomSaveState(romId, slot, name)); } catch (error) {}
  if (!renamed) {
    showToast('Could not rename save state');
    return;
  }

  save.label = name;
  closeRecentSaveRenameModal();
  refreshRomRecentSaves(card);
  showToast(`Renamed to ${name}`);
}

recentSaveRenameAction?.addEventListener('click', openRecentSaveRenameModal);
recentSaveDeleteAction?.addEventListener('click', () => {
  const card = pendingRecentSaveCard;
  const save = pendingRecentSave;
  closeRecentSaveActionsModal();
  if (card && save) deleteRecentSave(card, save);
});
closeRecentSaveActions?.addEventListener('click', closeRecentSaveActionsModal);
recentSaveActionsModal?.addEventListener('click', event => {
  if (event.target === recentSaveActionsModal) closeRecentSaveActionsModal();
});
cancelRecentSaveRename?.addEventListener('click', closeRecentSaveRenameModal);
saveRecentSaveRename?.addEventListener('click', commitRecentSaveRename);
recentSaveRenameModal?.addEventListener('click', event => {
  if (event.target === recentSaveRenameModal) closeRecentSaveRenameModal();
});
recentSaveNameInput?.addEventListener('keydown', event => {
  if (event.key === 'Enter') commitRecentSaveRename();
  if (event.key === 'Escape') closeRecentSaveRenameModal();
});

function deleteRecentSave(card, save){
  const romId = String(card?.dataset?.romId || '');
  const slot = Number(save?.slot);
  if (!romId || !Number.isInteger(slot) || slot < 0 || slot > 10) return;
  const label = String(save?.label || (slot === 0 ? 'Quick' : `Slot ${slot}`));
  const romTitle = String(card?.dataset?.title || 'this ROM');

  openConfirmModal(
    'Delete save state?',
    `Delete ${label} from ${romTitle}? This cannot be undone.`,
    () => {
      if (!(window.AndroidBridge && typeof window.AndroidBridge.deleteRomSaveState === 'function')) {
        showToast('Save deletion is only available in the Android build');
        return;
      }
      let deleted = false;
      try {
        deleted = Boolean(window.AndroidBridge.deleteRomSaveState(romId, slot));
      } catch (error) {}
      if (deleted) {
        refreshRomRecentSaves(card);
        updateStatistics();
        showToast(`${label} deleted`);
      } else {
        refreshRomRecentSaves(card);
        showToast(`Could not delete ${label}`);
      }
    }
  );
}

function bindRecentSaveLongPress(item, card, save){
  let timer = null;
  let pointerId = null;
  let startX = 0;
  let startY = 0;
  let triggered = false;

  const clear = () => {
    if (timer) clearTimeout(timer);
    timer = null;
  };

  item.addEventListener('pointerdown', event => {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    clear();
    pointerId = event.pointerId;
    startX = event.clientX;
    startY = event.clientY;
    triggered = false;
    item.classList.add('holding');
    timer = setTimeout(() => {
      triggered = true;
      suppressRecentSaveClickUntil = Date.now() + 750;
      item.classList.remove('holding');
      item.classList.add('long-pressed');
      if (navigator.vibrate) navigator.vibrate(12);
      openRecentSaveActions(card, save);
    }, recentSaveLongPressMs);
  });

  item.addEventListener('pointermove', event => {
    if (pointerId !== event.pointerId || !timer) return;
    if (Math.hypot(event.clientX - startX, event.clientY - startY) > recentSaveMoveTolerance) {
      clear();
      item.classList.remove('holding');
    }
  });

  ['pointerup', 'pointercancel', 'pointerleave'].forEach(eventName => {
    item.addEventListener(eventName, event => {
      if (pointerId !== null && event.pointerId !== undefined && event.pointerId !== pointerId) return;
      clear();
      item.classList.remove('holding');
      if (triggered) {
        suppressRecentSaveClickUntil = Date.now() + 750;
        setTimeout(() => item.classList.remove('long-pressed'), 140);
      }
      pointerId = null;
    });
  });

  item.addEventListener('contextmenu', event => {
    event.preventDefault();
    clear();
    suppressRecentSaveClickUntil = Date.now() + 750;
    openRecentSaveActions(card, save);
  });
}

function loadRecentSave(card, save){
  if (Date.now() < suppressRecentSaveClickUntil) return;
  const romId = String(card?.dataset?.romId || '');
  const slot = Number(save?.slot);
  if (!romId || !Number.isInteger(slot) || slot < 0 || slot > 10) return;

  if (window.AndroidBridge && typeof window.AndroidBridge.launchRomState === 'function') {
    pendingLaunchHistoryRomId = romId;
    window.AndroidBridge.launchRomState(
      romId,
      card.dataset.title || 'ROM',
      card.dataset.fileName || '',
      card.dataset.system || 'ROM',
      slot
    );
    return;
  }

  showToast('Save-state loading is only available in the Android build');
}

function refreshRomRecentSaves(card = currentRomCard){
  if (!detailEntries || !entriesCount || !card) return;
  const romId = String(card.dataset.romId || '');
  const nativeSaves = getNativeRomSaveStates(romId);
  let entries = nativeSaves;

  if (entries === null) {
    entries = (card.dataset.entries || '')
      .split(';')
      .map(value => value.trim())
      .filter(Boolean)
      .map((entry, index) => {
        const [title, time] = entry.split('|');
        return { slot: index, label: title || 'Save', displayTime: time || '' };
      });
  }

  detailEntries.replaceChildren();

  if (!entries.length) {
    const empty = document.createElement('div');
    empty.className = 'recent-saves-empty';
    empty.textContent = 'No save states yet.';
    detailEntries.appendChild(empty);
  } else {
    entries.forEach(save => {
      const item = document.createElement('div');
      item.className = 'entry-item recent-save-item';
      item.dataset.saveSlot = String(save.slot ?? '');

      const copy = document.createElement('div');
      copy.className = 'entry-copy';
      const title = document.createElement('strong');
      title.textContent = String(save.label || (Number(save.slot) === 0 ? 'Quick' : `Slot ${save.slot}`));
      const time = document.createElement('span');
      time.textContent = save.displayTime || formatRecentSaveTimestamp(save.modifiedAt);
      copy.append(title, time);

      const button = document.createElement('button');
      button.className = 'entry-download';
      button.type = 'button';
      button.setAttribute('aria-label', `Load ${title.textContent}`);
      button.innerHTML = '<svg class="recent-save-play-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M8.7 6.8c0-1.02 1.12-1.64 1.98-1.1l6.75 4.22c.82.51.82 1.65 0 2.16l-6.75 4.22c-.86.54-1.98-.08-1.98-1.1V6.8Z"/></svg>';
      button.addEventListener('click', event => {
        event.stopPropagation();
        loadRecentSave(card, save);
      });

      item.append(copy, button);
      bindRecentSaveLongPress(item, card, save);
      detailEntries.appendChild(item);
    });
  }

  entriesCount.textContent = `${entries.length} ${entries.length === 1 ? 'entry' : 'entries'}`;
}

function openRomDetail(card){
  libraryOverflowMenu?.classList.remove('open');
  setRomOverflowOpen(false);
  currentRomCard = card;
  const data = card.dataset;
  const coverSrc = card.querySelector('img')?.src || data.cover || '';

  detailTitle.textContent = data.title || 'ROM';
  detailAuthor.textContent = data.author || 'Unknown';
  detailStudio.textContent = data.studio || 'ROM';
  detailStatus.textContent = data.status || 'Ready to Play';
  detailSource.textContent = data.source || 'Local Library';
  detailSystem.textContent = data.system || 'GBA';
  detailDescription.textContent = '';
  detailDescription.hidden = true;

  detailCover.src = coverSrc;
  detailCover.alt = coverSrc ? `${data.title || 'ROM'} cover` : '';
  detailCover.hidden = !coverSrc;
  const detailCoverWrap = detailCover.closest('.rom-cover');
  detailCoverWrap?.classList.toggle('no-cover', !coverSrc);
  if (!coverSrc && detailCoverWrap) {
    detailCoverWrap.style.setProperty('--cover-fallback-a', card.style.getPropertyValue('--cover-fallback-a'));
    detailCoverWrap.style.setProperty('--cover-fallback-b', card.style.getPropertyValue('--cover-fallback-b'));
  }

  const nativeBackground = getNativeRomMediaUrl(card.dataset.romId, 'background');
  const bgImage = nativeBackground || card.dataset.bg || data.cover || coverSrc;
  if (bgImage) romHero.style.setProperty('--rom-hero-image', `url("${bgImage}")`);
  else romHero.style.removeProperty('--rom-hero-image');

  detailChips.innerHTML = '';
  const chipValues = inferGenreChips(data);
  chipValues.forEach(chip => {
    const span = document.createElement('span');
    span.className = 'genre-chip';
    span.textContent = chip;
    detailChips.appendChild(span);
  });

  refreshRomRecentSaves(card);
  updateRomLaunchAction(card);

  openSubPage('romDetailPage');
}

updateFloatingAddRomButton('libraryPage');
updateRomDetailState('libraryPage');

navItems.forEach(item => {
  item.addEventListener('click', () => setMainPage(item.dataset.page));
});

searchBtn.addEventListener('click', () => toggleSearch('library'));
historySearchBtn?.addEventListener('click', () => toggleSearch('history'));

addRomBtn?.addEventListener('click', () => {
  // Android WebView may retain touch focus after opening the picker, which can
  // leave the FAB in a stale pressed/focus visual state. Release focus while
  // keeping the click action unchanged.
  window.setTimeout(() => addRomBtn?.blur(), 0);
  if (!importNativeRom()) romFileInput?.click();
});

romFileInput?.addEventListener('change', async () => {
  await importRomFiles(romFileInput.files);
  romFileInput.value = '';
});

libraryMoreBtn.addEventListener('click', (event) => {
  event.preventDefault();
  event.stopPropagation();
  libraryOverflowMenu?.classList.toggle('open');
});

refreshLibraryBtn?.addEventListener('click', (event) => {
  event.preventDefault();
  event.stopPropagation();
  libraryOverflowMenu?.classList.remove('open');
  openConfirmModal(
    'Refresh library',
    'Refresh your library now?',
    () => {
      renderLibraryFromStorage();
      scheduleAutomaticCoverLookups(true);
      showToast('Library refreshed');
    }
  );
});
clearHistoryBtn.addEventListener('click', () => {
  const hasItems = historyList && historyList.children.length > 0;
  if (!hasItems) {
    showToast('History is already empty');
    return;
  }

  openConfirmModal(
    'Clear history',
    'Remove everything from history?',
    () => {
      playHistory = [];
      savePlayHistory();
      renderHistory();
      showToast('History cleared');
    }
  );
});
morePageBtn.addEventListener('click', () => showToast('More options'));

function setRomOverflowOpen(open){
  romOverflowMenu?.classList.toggle('open', !!open);
  romHero?.classList.toggle('rom-menu-open', !!open);
}

romMenuBtn.addEventListener('click', (event) => {
  event.preventDefault();
  event.stopPropagation();
  setRomOverflowOpen(!romOverflowMenu?.classList.contains('open'));
});

romOverflowMenu?.addEventListener('click', (event) => {
  event.stopPropagation();
});

document.addEventListener('click', (event) => {
  if (!romOverflowMenu || !romMenuBtn) return;
  if (!romOverflowMenu.contains(event.target) && !romMenuBtn.contains(event.target)) {
    setRomOverflowOpen(false);
  }
});

libraryOverflowMenu?.addEventListener('click', (event) => {
  event.stopPropagation();
});

document.addEventListener('click', (event) => {
  if (!libraryOverflowMenu || !libraryMoreBtn) return;
  if (!libraryOverflowMenu.contains(event.target) && !libraryMoreBtn.contains(event.target)) {
    libraryOverflowMenu.classList.remove('open');
  }
});

document.querySelectorAll('.continue-btn').forEach(btn => {
  btn.addEventListener('click', () => showToast('Launch ROM'));
});

searchInput.addEventListener('input', () => {
  const pageId = activeSearchContext === 'history' ? 'historyPage' : 'libraryPage';
  const state = { ...(pageUiState.get(pageId) || {}) };
  state.searchQuery = searchInput.value;
  state.searchOpen = searchPanel.classList.contains('open');
  pageUiState.set(pageId, state);
  persistPageUiState();
  runActiveSearch();
});



function filterCards(){
  const query = searchInput.value.trim().toLowerCase();
  const activeChip = document.querySelector('.chip.active');
  const category = activeChip?.dataset.category || 'Default';
  let visible = 0;

  getLibraryCards().forEach(card => {
    const title = (card.dataset.title || '').toLowerCase();
    const assigned = (card.dataset.category || '')
      .split(',')
      .map(v => v.trim())
      .filter(Boolean);
    const matchQuery = title.includes(query);
    const matchCategory = librarySelectionApi.matchesCategory(assigned, category);
    const show = matchQuery && matchCategory;

    card.style.display = show ? 'block' : 'none';
    if (show) visible++;
  });

  const noVisibleRoms = visible === 0;
  emptyState.classList.toggle('show', noVisibleRoms);
  if (noVisibleRoms) {
    const title = emptyState.querySelector('h3');
    const copy = emptyState.querySelector('p');
    if (getLibraryCards().length === 0) {
      if (title) title.textContent = 'No ROMs in your library';
      if (copy) copy.textContent = 'Tap + to add a ROM or ROM hack file from your device.';
    } else {
      if (title) title.textContent = 'No ROMs found';
      if (copy) copy.textContent = 'Try another title or category.';
    }
  }
}

function savePlayHistory(){
  localStorage.setItem(playHistoryStorageKey, JSON.stringify(playHistory));
  if (window.AndroidBridge && typeof window.AndroidBridge.syncRomPlayHistory === 'function') {
    try { window.AndroidBridge.syncRomPlayHistory(JSON.stringify(playHistory.slice(0, 100))); } catch (_) {}
  }
}

function isHistoryPaused(){
  return Boolean(incognitoHistoryToggle?.checked);
}

function formatHistoryTime(timestamp){
  const elapsed = Math.max(0, Date.now() - Number(timestamp || 0));
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  if (elapsed < minute) return 'Just now';
  if (elapsed < hour) return `${Math.floor(elapsed / minute)} min ago`;
  if (elapsed < day) return `${Math.floor(elapsed / hour)} hr ago`;
  if (elapsed < day * 7) return `${Math.floor(elapsed / day)} day${Math.floor(elapsed / day) === 1 ? '' : 's'} ago`;
  try {
    return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(timestamp));
  } catch (error) {
    return new Date(timestamp).toLocaleDateString();
  }
}

function getHistoryRom(entry){
  return libraryRoms.find(rom => String(rom.id) === String(entry?.romId)) || null;
}

function recordHistoryPlay(rom, nativeTitle = '', nativeSystem = ''){
  if (!rom || isHistoryPaused()) return;

  const id = String(rom.id || '');
  if (!id) return;
  const previous = playHistory.find(entry => String(entry.romId) === id);
  const nextEntry = {
    romId: id,
    title: String(nativeTitle || rom.title || 'ROM'),
    fileName: String(rom.fileName || ''),
    system: String(rom.system || nativeSystem || 'ROM'),
    playedAt: Date.now(),
    playCount: Math.max(0, Number(previous?.playCount) || 0) + 1
  };

  playHistory = [nextEntry, ...playHistory.filter(entry => String(entry.romId) !== id)].slice(0, 100);
  savePlayHistory();
  renderHistory();
}

async function hydrateHistoryCover(row, rom, nativeAlreadyChecked = false){
  if (!row || !rom?.id) return;

  // History must resolve artwork from the same sources as the Library. Native
  // artwork is checked first because automatic/manual covers may already exist
  // on disk even when the metadata cache has not been hydrated in this WebView.
  let cover = (nativeAlreadyChecked ? '' : getNativeRomMediaUrl(rom.id, 'cover')) || romCoverObjectUrls.get(rom.id) || rom.cover || '';
  if (!cover && rom.coverCached) {
    const blob = await getStoredRomCoverBlob(rom.id);
    if (blob) cover = setRomCoverObjectUrl(rom.id, blob);
  }
  if (!cover || !row.isConnected) return;

  const coverWrap = row.querySelector('.history-cover');
  if (!coverWrap) return;
  let image = coverWrap.querySelector('img');
  if (!image) {
    image = document.createElement('img');
    image.loading = 'lazy';
    image.decoding = 'async';
    image.alt = `${rom.title || 'ROM'} cover`;
    coverWrap.replaceChildren(image);
  }
  image.src = cover;
  coverWrap.classList.remove('no-cover');
}

function openHistoryRom(entry){
  const rom = getHistoryRom(entry);
  if (!rom) {
    showToast('This ROM is no longer in your Library');
    return;
  }
  const card = getLibraryCards().find(item => String(item.dataset.romId) === String(rom.id));
  if (!card) {
    renderLibraryFromStorage();
    const refreshed = getLibraryCards().find(item => String(item.dataset.romId) === String(rom.id));
    if (!refreshed) {
      showToast('ROM is unavailable');
      return;
    }
    openRomDetail(refreshed);
    return;
  }
  openRomDetail(card);
}

function resumeHistoryRom(entry){
  const rom = getHistoryRom(entry);
  if (!rom) {
    showToast('This ROM is no longer in your Library');
    return;
  }
  const card = getLibraryCards().find(item => String(item.dataset.romId) === String(rom.id));
  if (!card) {
    showToast('ROM is unavailable');
    return;
  }
  currentRomCard = card;
  resumeCurrentRom();
}

function renderHistory(){
  if (!historyList) return;
  historyList.innerHTML = '';

  playHistory
    .slice()
    .sort((a, b) => Number(b.playedAt || 0) - Number(a.playedAt || 0))
    .forEach(entry => {
      const row = document.createElement('article');
      row.className = 'history-item';
      row.dataset.romId = String(entry.romId || '');

      const coverWrap = document.createElement('div');
      coverWrap.className = 'history-cover no-cover';
      const rom = getHistoryRom(entry);
      const initialCover = rom ? (getNativeRomMediaUrl(rom.id, 'cover') || romCoverObjectUrls.get(rom.id) || rom.cover || '') : '';
      if (initialCover) {
        const image = document.createElement('img');
        image.src = initialCover;
        image.alt = `${entry.title || 'ROM'} cover`;
        coverWrap.appendChild(image);
        coverWrap.classList.remove('no-cover');
      } else {
        const placeholder = document.createElement('div');
        placeholder.className = 'history-cover-placeholder';
        placeholder.setAttribute('aria-hidden', 'true');
        coverWrap.appendChild(placeholder);
      }

      const copy = document.createElement('div');
      copy.className = 'history-copy';
      const title = document.createElement('strong');
      title.textContent = entry.title || rom?.title || 'ROM';
      const meta = document.createElement('span');
      const plays = Math.max(1, Number(entry.playCount) || 1);
      meta.textContent = `${formatHistoryTime(entry.playedAt)} • ${entry.system || rom?.system || 'ROM'} • ${plays} play${plays === 1 ? '' : 's'}`;
      copy.append(title, meta);

      const resume = document.createElement('button');
      resume.className = 'continue-btn';
      resume.type = 'button';
      resume.textContent = 'Resume';
      resume.disabled = !rom;
      resume.addEventListener('click', event => {
        event.stopPropagation();
        resumeHistoryRom(entry);
      });

      row.append(coverWrap, copy, resume);
      row.addEventListener('click', event => {
        if (event.target.closest('button')) return;
        openHistoryRom(entry);
      });
      historyList.appendChild(row);

      if (rom && !initialCover) {
        // Also covers stale metadata where native artwork exists but coverCached
        // has not been refreshed yet. The helper exits cheaply when unavailable.
        hydrateHistoryCover(row, rom, true);
      }
    });

  filterHistory();
}

function filterHistory(){
  const query = activeSearchContext === 'history' ? searchInput.value.trim().toLowerCase() : '';
  const items = [...document.querySelectorAll('#historyList .history-item')];
  let visible = 0;

  items.forEach(item => {
    const title = (item.querySelector('strong')?.textContent || '').toLowerCase();
    const show = title.includes(query);
    item.style.display = show ? 'grid' : 'none';
    if (show) visible++;
  });

  historyEmpty?.classList.toggle('show', visible === 0);
}

