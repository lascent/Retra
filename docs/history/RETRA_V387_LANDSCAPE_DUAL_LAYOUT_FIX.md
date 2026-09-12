# Retra v3.87 — Landscape UI + independent Screen Editor layouts

## Fixed

- Retra's normal Library UI now expands across the real landscape viewport instead of staying inside a centered 428px portrait shell.
- Landscape Library uses a responsive 3–6 column ROM grid, full-width header, bottom navigation, safe-area insets, and a correctly positioned Add ROM button.
- Browser-side portrait locking was removed; Android's own Screen orientation setting remains authoritative.
- Screen Editor portrait and landscape data were versioned to fresh v3.87 storage so previously corrupted mixed coordinates cannot return.
- Portrait and landscape controller data are loaded from separate keys and payload orientation is validated before use.
- A live editor state is blocked from ever being written to the other orientation's key.
- Rotation is now treated as a dataset swap, not as a layout edit.
- Android/WebView resize/orientation events are debounced until the viewport settles.
- The editor uses `visualViewport` dimensions when available to avoid transient old-orientation measurements.
- Re-opening Screen Editor after rotating elsewhere now forces reconciliation with the current device orientation.
- Fixed a stale-orientation early-return bug that could leave landscape controls active on the portrait canvas (or vice versa), causing controls to jump downward after rotation.
- Controller/screen transitions are disabled during the orientation swap so the target layout appears directly at its saved position.
- Native Android controller and emulator-screen preference keys are also separated under fresh v3.87 storage.

## Expected behavior

Portrait and landscape are two independent editor canvases. Editing Portrait never modifies Landscape, and editing Landscape never modifies Portrait. Rotating only switches which saved dataset is displayed.
