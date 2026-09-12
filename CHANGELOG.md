# Changelog

All notable Retra changes are documented here. Public release numbering follows Semantic Versioning beginning with `v1.0.0`.

## v1.0.0 — 2026-09-12

**First stable public release.**

### Added

- Persistent ROM identity based on stable content hashes and permanent `romId` records.
- GB/GBC/GBA library support through the mGBA integration, with Retra primarily focused on GBA workflows.
- Battery saves, save states, automatic resume, cheats, ROM patching, BIOS support, per-ROM layouts, artwork, and statistics.
- GBA Local Link plus Wi-Fi and Bluetooth Remote Link for supported normal Link Cable flows.
- Remote Link state-hash verification, host-authoritative recovery, jitter-aware input delay, validated input packets, and connection-health metrics.
- Screen Editor with independent portrait/landscape controller and emulator-screen layouts.
- Adaptive resize handles, keyboard nudging/resizing, and frame-coalesced pointer editing.
- Appearance themes, Color Style presets, translucent UI mode, controller opacity, and font controls.
- Optional GLSL shaders and advanced emulation/display settings.
- Google Drive API sync with conflict-safe behavior plus Android folder fallback.
- Automatic artwork with Wi-Fi-only mode and persistent manual-artwork precedence.
- ZIP/`.mgba` artwork title recovery from the inner playable ROM entry.
- Adaptive 60/90/120 Hz interface presentation without changing native emulator timing.

### Changed

- Refactored `MainActivity` into focused controllers and repositories.
- Split the Web UI into bounded JavaScript and CSS feature modules.
- Bounded network/background executor growth for lower-memory devices.
- Cached frame-skip state outside the emulation hot path.
- Unified touch targets, focus treatment, modal sizing, notices, and reduced-motion behavior.
- Improved Library multi-select responsiveness, popup placement, category handling, and artwork refresh behavior.
- Improved Settings safe-area handling and Home navigation warm-return performance.
- Improved audio/runtime behavior across normal speed, fast-forward, and slow-motion modes.

### Security and release hardening

- Serves the packaged Web UI from the AndroidX `WebViewAssetLoader` HTTPS origin.
- Disables cleartext traffic and universal file URL access.
- Includes regression tests, JavaScript syntax checks, release validation, Kotlin/JVM compile checks, Android lint, and CI quality gates.
- Pins the native build to NDK r28 and links for 16 KB page-size compatibility.
- Includes MPL-2.0 licensing and third-party notices.

### Known limitations

- GBA Single-Pak/Multiboot is not supported.
- GBA Wireless Adapter/RFU emulation is not supported.
- Full APK/AAB assembly requires a compatible validated mGBA source checkout under `third_party/mgba` or an explicitly configured source path.

---

# Development History

The entries below organize Retra's pre-1.0 development milestones into a clean `0.x` version sequence. These are development milestones leading to the first stable `v1.0.0` release.

## v0.9.12 — Final library polish and release hardening

- Finalized the solid Refresh Library popup and readability fixes.
- Completed final Screen Editor gesture, touch-target, keyboard-editing, Remote Link health, and release-gate hardening.

## v0.9.11 — MainActivity architecture refactor

- Reduced `MainActivity.kt` to a focused coordinator by extracting gameplay, multiplayer, layout, audio, settings, ROM, session, and persistence responsibilities.
- Restored compile-safe layout/configuration references after the refactor.

## v0.9.10 — GBA multiplayer recovery hardening

- Focused multiplayer support on GBA Local Link, Wi-Fi Remote Link, and Bluetooth Remote Link.
- Added automatic Link Cable eligibility checks and stronger Remote Link desync recovery.
- Kept Single-Pak/Multiboot and Wireless Adapter/RFU outside the supported scope.

## v0.9.9 — Shaders, Drive sync, and slow motion

- Added optional custom GLSL ES 2.0 shader installation.
- Added Google Drive REST sync with integrity checks and conflict-safe behavior.
- Added 0.2× and 0.5× slow-motion modes alongside existing normal/turbo speeds.

## v0.9.8 — Adaptive gameplay presentation

- Added Android VSync presentation for completed emulator frames.
- Added adaptive 60/90/120 Hz presentation without changing game or audio timing.

## v0.9.7 — Artwork and Settings reliability

- Improved manual and automatic artwork refresh behavior.
- Improved bottom safe-area scrolling for Settings detail pages.

## v0.9.6 — Architecture and maintainability pass

- Extracted long-lived storage, save/state, asset, statistics, WebView, and Remote Link responsibilities into focused components.
- Split large Web UI files into bounded feature modules and added architecture regression tests.

## v0.9.5 — Responsive ROM detail spacing

- Refined ROM detail top spacing across compact and larger Android screens.

## v0.9.4 — Category selection flow polish

- Made category saving automatically close Library multi-select mode after a successful update.

## v0.9.3 — Solid multi-select menu background

- Improved the Remove from Library popup background and readability over Library content.

## v0.9.2 — Multi-select popup placement refinement

- Improved popup positioning around the More action and compact phone safe areas.

## v0.9.1 — Android navigation-safe Settings

- Added native navigation-bar inset handling so final Settings rows remain reachable above gesture/3-button navigation.

## v0.9.0 — Menu close auto-save and exit

- Connected in-game Menu → Close to Retra's complete safe shutdown pipeline.
- Added automatic resume-state writing and atomic battery-save commit before returning to the Library.

## v0.8.9 — Multi-select popup positioning

- Moved Remove from Library into a clearer responsive position above the More action.

## v0.8.8 — Performance architecture and high-refresh UI

- Added adaptive display-performance ownership for 60/90/120 Hz UI presentation.
- Added warm-WebView return behavior and centralized background task executors.

## v0.8.7 — Unified dialog polish

- Standardized modal/dialog styling, spacing, inputs, and action treatment across the Retra UI.

## v0.8.6 — Automatic artwork

- Added optimized automatic artwork matching and persistent artwork handling.

## v0.8.5 — Library multi-select responsiveness

- Removed delayed selection behavior after long-press and improved selected-card rendering.
- Improved the multi-select More popup for compact displays.

## v0.8.4 — Pokémon Flash 1M compatibility

- Improved GBA ROM-hack Flash 1M / 128 KiB savedata compatibility.

## v0.8.3 — mGBA save autodetection fix

- Restored mGBA's per-cartridge savedata autodetection for modified GBA ROMs.

## v0.8.2 — Library multi-select polish

- Improved multi-select actions, Favourite/Unfavourite flow, and targeted ROM-card updates.

## v0.8.1 — GBA ROM-hack compatibility

- Fixed compatibility issues caused by overriding mGBA's automatic save-memory selection.

## v0.8.0 — Persistent ROM data foundation

- Introduced permanent ROM identities and persistent per-game data organization.
- Established the storage model that allows compatible user data to survive normal ROM file lifecycle changes.

For detailed engineering notes from development, see [`docs/README.md`](docs/README.md) and the archived files under `docs/history/`.
