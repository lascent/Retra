# Retra v4.41 — Architecture & maintainability refactor

## Goal
Raise Retra's maintainability without redesigning working UI or changing emulator timing, ROM identity, save compatibility, or user data behavior.

## Native Android boundaries
`MainActivity.kt` is reduced from about 6,381 lines in v4.40 to under 5,000 lines. Long-lived responsibilities now live in focused components:

- `RetraFileOps` — verified atomic copies/writes, hashing and persistent filesystem roots.
- `GameplayLayoutRepository` — per-ROM portrait/landscape layout persistence and legacy migration.
- `RomAssetRepository` — config plus manual cover/background persistence.
- `SaveDataRepository` — battery/RTC saves, working-copy recovery, backups and legacy migration.
- `SaveStateRepository` — manual/auto state paths, metadata, compatibility and deletion.
- `CheatRepository` — per-ROM cheat persistence.
- `SaveTransferRepository` — SAF tree export/import and cloud-folder synchronization.
- `StatisticsRepository` — playtime tracking/migration and save statistics.
- `WebUiController` — WebView security, appassets routing, renderer recovery and file chooser ownership.
- `RemoteLinkTransport` — Remote Link packet I/O, adaptive delay, heartbeat and resynchronization.

The Activity remains the gameplay/UI coordinator and JNI owner rather than also being every repository/service implementation.

## Web UI boundaries
The previous monolithic `script.js` is replaced by ordered modules:

`app-shell.js` → `library-ui.js` → `settings-ui.js` → `screen-editor.js` → `categories-ui.js` → `layout-profiles.js`.

The previous monolithic `style.css` is replaced by ordered cascade modules:

`style-core.css` → `style-screen-editor.css` → `style-controls.css` → `style-layout-polish.css` → `style-editor-modern.css` → `style-features.css` → `ui-polish.css`.

The split preserves execution/cascade order and keeps each feature file bounded enough to review independently.

## Regression guardrails
`tests/architecture-modularity.test.cjs` prevents the old JS/CSS monoliths from returning, caps MainActivity at 5,000 lines, verifies module ordering, and checks that filesystem/WebView/Remote Link ownership stays outside the Activity.

All prior behavior tests remain in place; architecture tests are additive rather than replacements for save/import/UI/performance checks.
