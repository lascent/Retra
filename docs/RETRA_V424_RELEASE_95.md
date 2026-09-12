# Retra v4.24 — 9.5 Release-Quality Pass

This pass keeps the persistent ROM/save architecture intact and focuses on the gaps that previously prevented Retra from being considered release-quality.

## Added / fixed

- True Library multi-select: long-press enters selection mode; subsequent taps select/deselect more ROMs.
- Accent-aware selected-card treatment and a responsive bottom contextual action bar.
- Bulk Categories, Favourite/Unfavourite, Remove from Library, and Delete Game Data actions.
- Mixed category assignments are preserved until explicitly changed.
- Default category now correctly means ROMs with no custom category.
- Keyboard-accessible ROM cards with focus/pressed states.
- Release metadata restored to 4.24 / 424 and release optimization enabled.
- Unfinished shader installer removed from release UI.
- WebView migrated from file:// to AndroidX WebViewAssetLoader HTTPS app assets.
- Universal file URL access disabled; cleartext traffic disabled; CSP added.
- WebView renderer-loss handling added.
- Android backup/device-transfer rules exclude managed ROM binaries and transient working saves while preserving user data.
- CMake now prefers project-local third_party/mgba, with legacy sibling and explicit-path fallbacks.
- Added direct multi-select tests, persistent-ROM architecture contract tests, release-hardening tests, device-side RomIdentityStore instrumentation tests, and GitHub Actions regression checks.

## Automated validation completed

- `node --check` passes for `script.js` and `library-selection.js`.
- 15/15 dependency-free Node regression tests pass.
- Manifest/backup/data-extraction XML parses successfully.
- Release validator reports 215 unique HTML IDs and no exposed release placeholders.
- CSS parser reports zero parse errors for `style.css` and `ui-polish.css`.

## Required before a public APK release

Run an Android build with the exact mGBA revision used by Retra, then test on physical devices:

- first import and duplicate import
- remove/re-import after rename or move
- battery save + rotating backup recovery
- save-state compatibility
- multi-select with 1, 2 and many ROMs
- bulk categories/favourites/remove/delete-data confirmations
- portrait/landscape controller-layout persistence
- GB, GBC and GBA gameplay
- Local Link and Remote Link
- app restart, upgrade install and device-transfer/restore

A public release should only be called 10/10 after those device checks pass.
