let nativeInsetsConnected = false;
const nativeSafeInsets = { top: 0, right: 0, bottom: 0, left: 0 };

window.retraSetNativeSafeInsets = (top, right, bottom, left) => {
  nativeInsetsConnected = true;
  const normalize = value => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? Math.max(0, Math.round(parsed)) : 0;
  };
  nativeSafeInsets.top = normalize(top);
  nativeSafeInsets.right = normalize(right);
  nativeSafeInsets.bottom = normalize(bottom);
  nativeSafeInsets.left = normalize(left);
  updateDeviceSafeInsets();
};

// Backward-compatible entry point for older native builds during upgrades.
window.retraSetNativeBottomInset = value => {
  nativeInsetsConnected = true;
  const parsed = Number(value);
  nativeSafeInsets.bottom = Number.isFinite(parsed) ? Math.max(0, Math.round(parsed)) : 0;
  updateDeviceSafeInsets();
};

function updateDeviceSafeInsets(){
  const root = document.documentElement;
  if (nativeInsetsConnected) root.dataset.retraRuntime = 'android';
  // Do not guess Android navigation heights from viewport size. Native
  // WindowInsets are authoritative; CSS env() remains the browser fallback.
  root.style.setProperty('--device-top-ui', `${nativeSafeInsets.top}px`);
  root.style.setProperty('--device-right-ui', `${nativeSafeInsets.right}px`);
  root.style.setProperty('--device-bottom-ui', `${nativeSafeInsets.bottom}px`);
  root.style.setProperty('--device-left-ui', `${nativeSafeInsets.left}px`);
}

previewScaleHandle?.addEventListener('pointerdown', event => {
  if (screenFrameSelected) beginScreenScale(event);
  else beginPreviewScale(event);
});

applyScreenSizePreview();
updateDeviceSafeInsets();

if ('ResizeObserver' in window && emulatorPreview) {
  const previewResizeObserver = new ResizeObserver(() => {
    if (screenEditorOrientationSwitchInProgress) return;
    if (previewLayoutInitialized && previewLayoutInitializedOrientation !== currentEditorOrientation()) return;
    updateResponsiveScreenFrame();
    applyPreviewLayoutControls();
  });
  previewResizeObserver.observe(emulatorPreview);
}
window.addEventListener('resize', () => requestAnimationFrame(() => {
  updateDeviceSafeInsets();
  if (screenEditorOrientationSwitchInProgress) return;
  if (previewLayoutInitialized && previewLayoutInitializedOrientation !== currentEditorOrientation()) return;
  updateResponsiveScreenFrame();
  applyPreviewLayoutControls();
}));
window.addEventListener('orientationchange', () => setTimeout(() => {
  // Safe-area values can update immediately, but controller/screen geometry is
  // restored only by the debounced orientation dataset swap above.
  updateDeviceSafeInsets();
}, 80));

if (window.visualViewport){
  window.visualViewport.addEventListener('resize', updateDeviceSafeInsets);
  window.visualViewport.addEventListener('scroll', updateDeviceSafeInsets);
}

const biosFileRow = document.getElementById('biosFileRow');

if (biosToggle && biosFileRow){
  const syncBiosFileRow = () => {
    const enabled = biosToggle.checked;
    biosFileRow.classList.toggle('disabled-row', !enabled);
    biosFileRow.disabled = !enabled;
  };
  biosToggle.addEventListener('change', syncBiosFileRow);
  syncBiosFileRow();
}
biosFileRow?.addEventListener('click', () => {
  if (biosFileRow.disabled) return;
  if (window.AndroidBridge && typeof window.AndroidBridge.selectBiosFile === 'function') {
    window.AndroidBridge.selectBiosFile();
  } else {
    showToast('BIOS file selection is available in the Android app');
  }
});

function readImageFile(input, callback){
  const file = input.files && input.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => callback(reader.result);
  reader.readAsDataURL(file);
}

function triggerBackgroundChange(){
  bgFileInput.click();
}

function triggerCoverChange(){
  coverFileInput.click();
}

menuChangeBgBtn?.addEventListener('click', (event) => {
  event.preventDefault();
  event.stopPropagation();
  setRomOverflowOpen(false);
  triggerBackgroundChange();
});
menuChangeCoverBtn?.addEventListener('click', (event) => {
  event.preventDefault();
  event.stopPropagation();
  setRomOverflowOpen(false);
  triggerCoverChange();
});

bgFileInput.addEventListener('change', () => {
  readImageFile(bgFileInput, (result) => {
    const romId = currentRomCard?.dataset?.romId || '';
    if (romId && saveNativeRomMedia(romId, 'background', result)) {
      const url = getNativeRomMediaUrl(romId, 'background') || result;
      romHero.style.setProperty('--rom-hero-image', `url("${url}")`);
      currentRomCard.dataset.bg = url;
      showToast('Background saved permanently');
    } else {
      romHero.style.setProperty('--rom-hero-image', `url("${result}")`);
      if (currentRomCard) currentRomCard.dataset.bg = result;
      showToast('Background updated');
    }
    bgFileInput.value = '';
  });
});

coverFileInput.addEventListener('change', () => {
  const file = coverFileInput.files && coverFileInput.files[0];
  if (!file) return;
  const rom = currentRomCard ? getRomById(currentRomCard.dataset.romId) : null;
  if (!rom) return;

  const reader = new FileReader();
  reader.onload = async () => {
    try {
      const dataUrl = String(reader.result || '');
      if (saveNativeRomMedia(rom.id, 'cover', dataUrl)) {
        // The file is already permanent at this point. If WebView asset routing
        // has not observed the new file yet, show the selected data URL now and
        // the next render will naturally use the persistent appassets URL.
        const coverUrl = getNativeRomMediaUrl(rom.id, 'cover') || dataUrl;
        rom.coverCached = true;
        rom.coverSource = 'manual';
        delete rom.cover;
        delete rom.coverLookupCheckedAt;
        saveLibraryRoms();
        applyCoverToRenderedRom(rom, coverUrl, currentRomCard);
        showToast('Cover saved permanently');
      } else {
        // IndexedDB remains a compatibility fallback for WebView/provider edge
        // cases. A failure here is the only condition that should reject the
        // manual cover operation.
        await storeRomCoverBlob(rom.id, file, 'manual');
        const coverUrl = setRomCoverObjectUrl(rom.id, file);
        rom.coverCached = true;
        rom.coverSource = 'manual';
        delete rom.cover;
        delete rom.coverLookupCheckedAt;
        saveLibraryRoms();
        applyCoverToRenderedRom(rom, coverUrl, currentRomCard);
        showToast('Cover saved');
      }
    } catch (error) {
      showToast('Could not save cover');
    } finally {
      coverFileInput.value = '';
    }
  };
  reader.onerror = () => {
    coverFileInput.value = '';
    showToast('Could not read cover');
  };
  reader.readAsDataURL(file);
});

async function resumeCurrentRom(){
  const romId = currentRomCard?.dataset.romId;
  if (!romId) {
    showToast('Select a ROM first');
    return;
  }

  const wantsResume = resumeAction?.dataset.launchMode === 'resume' || nativeHasResumeState(romId);
  if (launchNativeRom(currentRomCard, wantsResume)) return;

  const file = await getStoredRomFile(romId);
  if (!file) {
    showToast('ROM file is unavailable. Add it again with +');
    return;
  }

  showToast(`ROM ready: ${file.name}`);
}

resumeAction.addEventListener('click', resumeCurrentRom);
bigResumeBtn?.addEventListener('click', resumeCurrentRom);


const libraryChips = document.getElementById('libraryChips');
const categoriesList = document.getElementById('categoriesList');
const categoriesEmpty = document.getElementById('categoriesEmpty');
const addCategoryBtn = document.getElementById('addCategoryBtn');
const categoryModal = document.getElementById('categoryModal');
const categoryNameInput = document.getElementById('categoryNameInput');
const cancelCategoryBtn = document.getElementById('cancelCategoryBtn');
const saveCategoryBtn = document.getElementById('saveCategoryBtn');

let romCategoryAssignments = readCategoryMap('retraRomCategoryAssignments');
let pendingRomCategories = [];

// v4.18: each ROM owns its own saved/default category snapshot.
// The built-in "Default" Library chip contains ROMs with no custom category.
// Assigning a custom category moves a ROM out of Default until its assignments are cleared.
const romCategoryPerRomDefaultsStorageKey = 'retraRomCategoryPerRomDefaultsV1';
let romCategoryPerRomDefaults = {};
try {
  const stored = JSON.parse(localStorage.getItem(romCategoryPerRomDefaultsStorageKey) || '{}');
  romCategoryPerRomDefaults = stored && typeof stored === 'object' && !Array.isArray(stored) ? stored : {};
} catch (error) {
  romCategoryPerRomDefaults = {};
}

function sanitizeCategoryList(list){
  return [...new Set(Array.isArray(list) ? list : [])].filter(category => customCategories.includes(category));
}

function savePerRomCategoryDefaults(){
  Object.keys(romCategoryPerRomDefaults).forEach(key => {
    romCategoryPerRomDefaults[key] = sanitizeCategoryList(romCategoryPerRomDefaults[key]);
  });
  localStorage.setItem(romCategoryPerRomDefaultsStorageKey, JSON.stringify(romCategoryPerRomDefaults));
}

function getPerRomDefaultCategories(key){
  return sanitizeCategoryList(romCategoryPerRomDefaults[key]);
}

function applyDefaultCategoriesToRomKey(key){
  // New ROMs belong to the built-in Default view and no custom category by default.
  if (!key || Array.isArray(romCategoryAssignments[key])) return;
  romCategoryAssignments[key] = [];
  saveRomAssignments();
}

function saveRomAssignments(){
  localStorage.setItem('retraRomCategoryAssignments', JSON.stringify(romCategoryAssignments));
  [...libraryRoms, ...archivedLibraryRoms].forEach(syncRomMetadataToNative);
}

function getRomKey(cardOrTitle){
  return typeof cardOrTitle === 'string' ? cardOrTitle : (cardOrTitle?.dataset.romId || cardOrTitle?.dataset.title || '');
}

function getAssignedCategoriesForKey(key){
  return Array.isArray(romCategoryAssignments[key]) ? romCategoryAssignments[key] : [];
}

function syncAssignmentsToCards(){
  getLibraryCards().forEach(card => {
    const key = getRomKey(card);
    const assigned = getAssignedCategoriesForKey(key);
    card.dataset.category = assigned.join(',');
  });
}

let customCategories = readCustomCategories();

function closeMultiCategoryModal(){
  multiCategoryModal?.classList.remove('open');
  multiCategoryModal?.setAttribute('aria-hidden', 'true');
  pendingMultiCategoryTouched.clear();
}

function createMultiCategoryRow(category, state, isDefault = false){
  const row = document.createElement('label');
  row.className = 'multi-category-item';
  row.dataset.category = category;
  row.innerHTML = `
    <span class="multi-category-check"><input type="checkbox"><span class="multi-category-box"></span></span>
    <span class="multi-category-name"><strong>${escapeHtml(category)}</strong><small>${isDefault ? 'ROMs with no custom category' : `Show selected ROMs under ${escapeHtml(category)}`}</small></span>
  `;
  const input = row.querySelector('input');
  input.checked = state === 'all';
  input.indeterminate = state === 'mixed';
  row.classList.toggle('mixed', state === 'mixed');

  input.addEventListener('change', () => {
    input.indeterminate = false;
    row.classList.remove('mixed');
    pendingMultiCategoryTouched.add(category);

    if (isDefault && input.checked) {
      [...multiCategoryList.querySelectorAll('.multi-category-item')].forEach(otherRow => {
        if (otherRow === row) return;
        const otherInput = otherRow.querySelector('input');
        otherInput.checked = false;
        otherInput.indeterminate = false;
        otherRow.classList.remove('mixed');
        pendingMultiCategoryTouched.add(otherRow.dataset.category);
      });
    } else if (!isDefault && input.checked) {
      const defaultRow = multiCategoryList.querySelector('[data-category="Default"]');
      const defaultInput = defaultRow?.querySelector('input');
      if (defaultInput) {
        defaultInput.checked = false;
        defaultInput.indeterminate = false;
        defaultRow.classList.remove('mixed');
      }
    }
  });
  return row;
}

function openMultiCategoryModal(){
  const selected = getSelectedLibraryRoms();
  if (!selected.length || !multiCategoryList) return;
  const assignments = selected.map(rom => getAssignedCategoriesForKey(String(rom.id)));
  pendingMultiCategoryTouched.clear();
  multiCategoryList.innerHTML = '';
  if (multiCategoryCopy) {
    multiCategoryCopy.textContent = selected.length === 1
      ? 'Choose where this ROM appears. Default means no custom category.'
      : `Apply categories to ${selected.length} ROMs. Mixed categories stay unchanged unless you edit them.`;
  }

  multiCategoryList.appendChild(createMultiCategoryRow(
    'Default',
    librarySelectionApi.categoryState(assignments, 'Default'),
    true
  ));
  customCategories.forEach(category => {
    multiCategoryList.appendChild(createMultiCategoryRow(
      category,
      librarySelectionApi.categoryState(assignments, category),
      false
    ));
  });

  multiCategoryModal.classList.add('open');
  multiCategoryModal.setAttribute('aria-hidden', 'false');
  setLibrarySelectionMoreOpen(false);
}

function saveMultiSelectedCategories(){
  const selected = getSelectedLibraryRoms();
  if (!selected.length) return closeMultiCategoryModal();
  const defaultInput = multiCategoryList?.querySelector('[data-category="Default"] input');
  const forceDefault = pendingMultiCategoryTouched.has('Default') && Boolean(defaultInput?.checked);

  selected.forEach(rom => {
    const key = String(rom.id);
    if (forceDefault) {
      romCategoryAssignments[key] = [];
      return;
    }

    const next = new Set(getAssignedCategoriesForKey(key).filter(category => customCategories.includes(category)));
    customCategories.forEach(category => {
      if (!pendingMultiCategoryTouched.has(category)) return;
      const row = [...multiCategoryList.querySelectorAll('.multi-category-item')]
        .find(item => item.dataset.category === category);
      const checked = Boolean(row?.querySelector('input')?.checked);
      if (checked) next.add(category);
      else next.delete(category);
    });
    romCategoryAssignments[key] = [...next];
  });

  const selectedCount = selected.length;
  saveRomAssignments();
  syncAssignmentsToCards();
  filterCards();

  // Saving categories completes the bulk action. Close the modal and leave
  // multi-select mode immediately so the Library returns to its normal state.
  closeMultiCategoryModal();
  exitLibrarySelection();
  showToast(`Categories updated for ${selectedCount} ROM${selectedCount === 1 ? '' : 's'}`);
}

function saveCategories(){
  localStorage.setItem('retraCategories', JSON.stringify(customCategories));
}

function bindCategoryChips(){
  chips = document.querySelectorAll('.chip');
  chips.forEach(chip => {
    chip.onclick = () => {
      chips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      filterCards();
    };
  });
}

function renderLibraryCategories(){
  const activeCategory = libraryChips.querySelector('.chip.active')?.dataset.category || 'Default';
  libraryChips.innerHTML = '<button class="chip active" data-category="Default">Default</button>';

  customCategories.forEach(category => {
    const btn = document.createElement('button');
    btn.className = 'chip';
    btn.dataset.category = category;
    btn.textContent = category;
    libraryChips.appendChild(btn);
  });

  const restoredChip = [...libraryChips.querySelectorAll('.chip')].find(chip => chip.dataset.category === activeCategory);
  if (restoredChip) {
    libraryChips.querySelector('.active')?.classList.remove('active');
    restoredChip.classList.add('active');
  }
  bindCategoryChips();
}

function renderCategoriesPage(){
  categoriesList.innerHTML = '';

  if (customCategories.length === 0){
    categoriesEmpty.classList.remove('hidden');
    return;
  }

  categoriesEmpty.classList.add('hidden');

  customCategories.forEach(category => {
    const row = document.createElement('div');
    row.className = 'category-row';
    row.innerHTML = `
      <div>
        <strong>${escapeHtml(category)}</strong>
        <span>Custom library category</span>
      </div>
      <button class="category-delete" aria-label="Delete ${escapeHtml(category)}">
        <svg viewBox="0 0 24 24">
          <path d="M4 7h16"/>
          <path d="M9 7V4h6v3"/>
          <path d="m7 7 1 13h8l1-13"/>
        </svg>
      </button>
    `;

    row.querySelector('.category-delete').addEventListener('click', () => {
      customCategories = customCategories.filter(c => c !== category);
      saveCategories();

      Object.keys(romCategoryAssignments).forEach(key => {
        romCategoryAssignments[key] = (romCategoryAssignments[key] || []).filter(c => c !== category);
      });
      saveRomAssignments();
      Object.keys(romCategoryPerRomDefaults).forEach(key => {
        romCategoryPerRomDefaults[key] = (romCategoryPerRomDefaults[key] || []).filter(c => c !== category);
      });
      savePerRomCategoryDefaults();
      syncAssignmentsToCards();

      renderCategoriesPage();
      renderLibraryCategories();
      filterCards();
      showToast(`${category} removed`);
    });

    categoriesList.appendChild(row);
  });
}


function closeRomFilterOptions(){
  romFilterOptionsMenu?.classList.remove('open');
  romFilterOptionsMenu?.setAttribute('aria-hidden', 'true');
  romFilterMoreBtn?.setAttribute('aria-expanded', 'false');
}

function toggleRomFilterOptions(event){
  event?.preventDefault();
  event?.stopPropagation();
  if (!romFilterOptionsMenu) return;
  const willOpen = !romFilterOptionsMenu.classList.contains('open');
  romFilterOptionsMenu.classList.toggle('open', willOpen);
  romFilterOptionsMenu.setAttribute('aria-hidden', willOpen ? 'false' : 'true');
  romFilterMoreBtn?.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
}

function setCurrentCategoriesAsDefault(){
  if (!currentRomCard) return;
  const key = getRomKey(currentRomCard);
  const saved = sanitizeCategoryList(pendingRomCategories);

  // Save both the ROM's live assignment and its personal reset point so the
  // three-dot action behaves immediately and predictably.
  romCategoryAssignments[key] = [...saved];
  romCategoryPerRomDefaults[key] = [...saved];
  saveRomAssignments();
  savePerRomCategoryDefaults();
  syncAssignmentsToCards();
  filterCards();
  closeRomFilterOptions();
  renderRomFilterSheet();

  showToast(saved.length ? 'Default categories saved for this ROM' : 'Default set to the main Library');
}

function resetCurrentCategoriesToDefault(){
  if (!currentRomCard) return;
  const key = getRomKey(currentRomCard);
  const restored = getPerRomDefaultCategories(key);

  pendingRomCategories = [...restored];
  romCategoryAssignments[key] = [...restored];
  saveRomAssignments();
  syncAssignmentsToCards();
  filterCards();
  closeRomFilterOptions();
  renderRomFilterSheet();

  showToast(restored.length ? "Returned to this ROM's default categories" : "Returned to Default");
}


function renderRomFilterSheet(){
  const key = currentRomCard ? getRomKey(currentRomCard) : '';
  const displayTitle = currentRomCard?.dataset.title || '';
  const selected = new Set(pendingRomCategories);
  sheetCategoryList.innerHTML = '';
  sheetRomTitle.textContent = displayTitle ? `Select categories for ${displayTitle}.` : 'Select where this ROM should appear in your Library.';

  if (customCategories.length === 0){
    sheetCategoryEmpty.classList.add('show');
    return;
  }

  sheetCategoryEmpty.classList.remove('show');

  customCategories.forEach(category => {
    const row = document.createElement('label');
    row.className = 'sheet-category-item';
    row.innerHTML = `
      <div class="sheet-category-check">
        <input type="checkbox" ${selected.has(category) ? 'checked' : ''}>
        <span class="sheet-category-box"></span>
      </div>
      <div class="sheet-category-name">
        <strong>${escapeHtml(category)}</strong>
        <span>Show this ROM under ${escapeHtml(category)}</span>
      </div>
      <div class="sheet-category-count">Library</div>
    `;
    const checkbox = row.querySelector('input');
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) {
        if (!pendingRomCategories.includes(category)) pendingRomCategories.push(category);
      } else {
        pendingRomCategories = pendingRomCategories.filter(c => c !== category);
      }
    });
    sheetCategoryList.appendChild(row);
  });
}

function openRomFilterSheet(){
  if (!currentRomCard) return;
  pendingRomCategories = [...getAssignedCategoriesForKey(getRomKey(currentRomCard))];
  closeRomFilterOptions();
  renderRomFilterSheet();
  romFilterSheetBackdrop.classList.add('open');
}

function closeRomFilter(){
  closeRomFilterOptions();
  romFilterSheetBackdrop.classList.remove('open');
}

function saveCurrentRomCategories(){
  if (!currentRomCard) return;
  const key = getRomKey(currentRomCard);
  romCategoryAssignments[key] = [...new Set(pendingRomCategories)];
  saveRomAssignments();
  syncAssignmentsToCards();
  filterCards();
  closeRomFilter();
  showToast('Categories updated');
}

function openCategoryModal(){
  categoryNameInput.value = '';
  categoryModal.classList.add('open');
  setTimeout(() => categoryNameInput.focus(), 100);
}

function closeCategoryModal(){
  categoryModal.classList.remove('open');
}

function addCategory(){
  const name = categoryNameInput.value.trim();

  if (!name){
    showToast('Enter a category name');
    return;
  }

  if (['default', 'all'].includes(name.toLowerCase())){
    showToast('Default is the built-in Library category');
    return;
  }

  if (customCategories.some(c => c.toLowerCase() === name.toLowerCase())){
    showToast('Category already exists');
    return;
  }

  customCategories.push(name);
  saveCategories();
  renderCategoriesPage();
  renderLibraryCategories();
  closeCategoryModal();
  showToast(`${name} added`);
}

addCategoryBtn.addEventListener('click', openCategoryModal);
cancelCategoryBtn.addEventListener('click', closeCategoryModal);
saveCategoryBtn.addEventListener('click', addCategory);

categoryNameInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter') addCategory();
});

categoryModal.addEventListener('click', (event) => {
  if (event.target === categoryModal) closeCategoryModal();
});

romFilterBtn?.addEventListener('click', openRomFilterSheet);
closeRomFilterSheet?.addEventListener('click', closeRomFilter);
saveRomCategoriesBtn?.addEventListener('click', saveCurrentRomCategories);
romFilterSheetBackdrop?.addEventListener('click', (event) => {
  if (event.target === romFilterSheetBackdrop) closeRomFilter();
});

romFilterMoreBtn?.addEventListener('click', toggleRomFilterOptions);
setFilterDefaultBtn?.addEventListener('click', setCurrentCategoriesAsDefault);
resetFilterDefaultBtn?.addEventListener('click', resetCurrentCategoriesToDefault);
romFilterOptionsMenu?.addEventListener('click', event => event.stopPropagation());
document.addEventListener('click', event => {
  if (!romFilterOptionsMenu?.classList.contains('open')) return;
  if (romFilterOptionsMenu.contains(event.target) || romFilterMoreBtn?.contains(event.target)) return;
  closeRomFilterOptions();
});

librarySelectionClose?.addEventListener('click', exitLibrarySelection);
librarySelectionCategories?.addEventListener('click', openMultiCategoryModal);
librarySelectionFavorite?.addEventListener('click', () => {
  const selected = getSelectedLibraryRoms();
  if (!selected.length) return;
  const selectedCount = selected.length;
  const favorite = librarySelectionApi.shouldFavoriteAll(selected);
  selected.forEach(rom => { rom.favorite = favorite; });
  saveLibraryRoms();

  // Favourite is a complete one-tap action: close selection mode immediately
  // instead of making the user manually dismiss the action bar afterwards.
  exitLibrarySelection();
  renderLibraryFromStorage();
  showToast(favorite
    ? `${selectedCount} ROM${selectedCount === 1 ? '' : 's'} added to Favourites`
    : `${selectedCount} ROM${selectedCount === 1 ? '' : 's'} removed from Favourites`);
});
librarySelectionMore?.addEventListener('click', event => {
  event.preventDefault();
  event.stopPropagation();
  setLibrarySelectionMoreOpen(!librarySelectionMoreMenu?.classList.contains('open'));
});
librarySelectionMoreMenu?.addEventListener('click', event => event.stopPropagation());
librarySelectionRemove?.addEventListener('click', () => {
  const selected = getSelectedLibraryRoms();
  if (!selected.length) return;
  setLibrarySelectionMoreOpen(false);
  openConfirmModal(
    'Remove from Library?',
    `Remove ${selected.length} selected ROM${selected.length === 1 ? '' : 's'} from the Library? Saves, states, cheats, favourites, categories and other game data will be kept and restored when the same ROM is added again.`,
    removeSelectedRomsFromLibrary
  );
});
multiCategoryCancel?.addEventListener('click', closeMultiCategoryModal);
multiCategorySave?.addEventListener('click', saveMultiSelectedCategories);
multiCategoryModal?.addEventListener('click', event => {
  if (event.target === multiCategoryModal) closeMultiCategoryModal();
});
document.addEventListener('click', event => {
  if (!librarySelectionMoreMenu?.classList.contains('open')) return;
  if (librarySelectionMoreMenu.contains(event.target) || librarySelectionMore?.contains(event.target)) return;
  setLibrarySelectionMoreOpen(false);
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && isLibrarySelectionMode()) exitLibrarySelection();
});

romFavoriteAction?.addEventListener('click', () => {
  const rom = getRomById(activeLibraryActionRomId);
  if (!rom) return closeLibraryRomActions();
  rom.favorite = !rom.favorite;
  saveLibraryRoms();
  const nowFavourite = rom.favorite;
  closeLibraryRomActions();
  renderLibraryFromStorage();
  showToast(nowFavourite ? 'Added to Favourites' : 'Removed from Favourites');
});

romRemoveAction?.addEventListener('click', () => {
  const rom = getRomById(activeLibraryActionRomId);
  if (!rom) return closeLibraryRomActions();
  const id = rom.id;
  const title = rom.title || 'this ROM';
  closeLibraryRomActions();
  openConfirmModal(
    'Remove from Library?',
    `Remove “${title}” from the Library? Saves, save states, cheats, favourites, categories and other game data will be kept and restored if you add the same ROM again.`,
    () => removeRomFromLibrary(id)
  );
});

romDeleteDataAction?.addEventListener('click', () => {
  const rom = getRomById(activeLibraryActionRomId);
  if (!rom) return closeLibraryRomActions();
  const id = rom.id;
  const title = rom.title || 'this ROM';
  closeLibraryRomActions();
  openConfirmModal(
    'Delete Game Data?',
    `Permanently delete saves, save states, cheats, backups, statistics and playtime for “${title}”? This cannot be undone. The ROM stays in your Library.`,
    () => deleteRomGameData(id)
  );
});

closeRomCardActions?.addEventListener('click', closeLibraryRomActions);
romCardActionsModal?.addEventListener('click', event => {
  if (event.target === romCardActionsModal) closeLibraryRomActions();
});

if (incognitoHistoryToggle) {
  incognitoHistoryToggle.checked = localStorage.getItem(incognitoHistoryStorageKey) === 'true';
  incognitoHistoryToggle.addEventListener('change', () => {
    localStorage.setItem(incognitoHistoryStorageKey, incognitoHistoryToggle.checked ? 'true' : 'false');
    showToast(incognitoHistoryToggle.checked ? 'Playing history paused' : 'Playing history resumed');
  });
}

renderHistory();
renderLibraryFromStorage();
syncAssignmentsToCards();
renderLibraryCategories();
renderCategoriesPage();
setMainPage('libraryPage');

confirmCancelBtn?.addEventListener('click', closeConfirmModal);

confirmModal?.addEventListener('click', (event) => {
  if (event.target === confirmModal) closeConfirmModal();
});

// v3.81.1 Android/WebView: do not let Settings launcher rows keep touch focus.
// WebView can paint a transient focus/hover state for the tapped button during the
// same frame that a selection modal opens. Blurring the launcher immediately keeps
// the background unchanged while preserving the selected state inside the modal.
(() => {
  const settingsLauncherSelector = '.settings-detail-page button.detail-row.tappable';

  document.addEventListener('pointerdown', (event) => {
    const row = event.target.closest?.(settingsLauncherSelector);
    if (!row) return;
    row.style.setProperty('-webkit-tap-highlight-color', 'transparent', 'important');
    row.style.setProperty('background', 'transparent', 'important');
  }, true);

  document.addEventListener('click', (event) => {
    const row = event.target.closest?.(settingsLauncherSelector);
    if (!row) return;
    row.blur();
  }, true);
})();

