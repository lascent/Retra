# Changelog

## v1.0.1 — 2026-09-13 — Backup, restore, storage, and audio polish

### Appearance — reference-driven Light mode
- Updated every Appearance palette with a dedicated Light variant based on the supplied Mihon light-theme references while preserving Retra's own layout and branding.
- Light/System mode now carries the selected palette through the app background, surfaces, navigation, Settings cards, accents, switches, and selected-mode segment instead of using one generic light scheme.
- Theme preview cards now switch to light previews whenever Light mode (or System resolving to light) is active.
- Added a matching Light variant for Retra's Aurora Mint palette and regression coverage for all light themes.

### Controller responsiveness and input latency
- Added pointer-owned, drift-tolerant low-latency touch handling for A/B and other native gameplay buttons.
- Added native rising-edge input latching so sub-frame taps cannot disappear between mGBA input polls.
- Explicitly enabled split multi-touch for the gameplay viewport/A-B group and hardened lifecycle stuck-key cleanup.
- Suppressed duplicate Android-side key transitions and unnecessary all-key release traffic.

### Release hardening: portable BIOS state, slider hot paths, reproducible mGBA and device tests

- Kept BIOS paths, enable/boot state, and last BIOS label device-local so `.retra` restore cannot enable a BIOS file that was intentionally excluded from the backup.
- Added specialized rate-limited native handling for controller opacity, frameskip, volume, and Link sync sliders; portable metadata is refreshed once at the final committed value instead of throughout the drag.
- Locked native builds to mGBA commit `543a197582c30364584d773a974d7f991892fa43`, added `tools/prepare_mgba.py`, and made CMake reject wrong/dirty/unverifiable mGBA source trees.
- Expanded Android instrumentation coverage for portable preference safety, atomic file operations, IME window policy/adaptive display selection, alongside the existing ROM identity/migration tests.
- Added a physical-device validation matrix for Google Drive, Wi-Fi Link, Bluetooth Link, IME, foldables/resizing, and 60/90/120 Hz behavior.

### Adaptive Android bottom safe areas

- Centralized Android system-bar and display-cutout handling through `WebUiInsetsManager` and native `WindowInsetsCompat`.
- Normal Retra Web UI now renders edge-to-edge while consuming the real device safe area exactly once.
- Removed the viewport-size navigation-height guess that could create artificial bottom gaps on emulators and phones.
- Fixed Library / History / More bottom navigation spacing for gesture, 2-button/legacy where available, and 3-button navigation.
- Moved the ROM category sheet Close / Save footer outside the scrolling body and made only that footer consume the bottom system inset.
- Centralized WebView safe-area CSS variables for top/right/bottom/left cutout handling, rotation, tablets, and landscape.
- Removed duplicated Settings detail-page bottom inset while retaining normal design-only scroll tail spacing.

- Added an ~18 Hz DC blocker after Retra's existing band-limited sample-rate conversion to remove sub-audible offset without cutting musical bass.
- Added gentle 22% mGBA-style single-pole smoothing to reduce gritty/harsh high-frequency character while preserving detail; this is intentionally far lighter than mGBA libretro's 60% default filter strength.
- Added 1% PCM headroom before the final saturating PCM16 handoff to Android.
- Kept mGBA's original mixer, game pitch, tempo, stereo image, speed synchronization, AudioTrack writer, and adaptive underrun handling unchanged.
- Added regression coverage for DC removal, smoothing strength, post-resampler placement, and output headroom.


### Backup UI alignment / fixed action bar

- Vertically centered the Create backup / Restore backup icons and copy in their action rows.
- Made the Create backup action a true fixed bottom bar so it does not move with checklist scrolling.
- Added bottom scroll clearance so the final backup option remains fully visible above the fixed action.

### Data and Storage backup / restore

- Added **Data and Storage** directly below **Color Style** in the More panel.
- Added selective portable backups for game saves, save states, cheats, library metadata, controller layouts, artwork, and app settings.
- Backups use a single `Retra_yyyyMMdd_HHmm.retra` file and Android's normal save picker so users can choose any supported destination.
- Added validated restore with path-traversal protection, size/entry limits, portable-setting filtering, and library metadata recovery for ROMs already known to Retra.
- ROM and BIOS files are intentionally excluded from `.retra` backups.

### Built-in Retra app folder

- Added a Retra `DocumentsProvider` so Android Files can show Retra as its own storage location.
- `Open app folder` now opens the Retra root directly instead of launching `ACTION_OPEN_DOCUMENT_TREE`.
- Removed the repeated `Use this folder` requirement from Retra's normal save/settings workflow.
- Saves and settings continue to use Retra-owned persistent storage; explicit import/export and Drive folder selection still use SAF where appropriate.

### Authentic audio and A/B alignment

- Fixed normal-speed game music pitch/tempo/tone by resampling mGBA's real dynamic core audio rate to the Android output rate with mGBA's windowed-sinc resampler.
- Aligned the default grouped A and B gameplay buttons to the same vertical centerline.
- Updated the Screen Editor preview so A no longer sits lower than B.
- Preserved the existing A/B group size and saved layout coordinates to avoid shifting user layouts.


All notable Retra changes are documented here. Public release numbering follows Semantic Versioning beginning with `v1.0.0`.

## v1.0.0 — 2026-09-12
### Authentic background-music output pass
- Route high-quality audio through the Android device-native output clock when appropriate, so mGBA's sinc resampler performs the explicit conversion directly instead of relying on an extra OS resample.
- Add bounded adaptive prebuffer/AudioTrack sizing after real underruns, with gradual recovery to the normal low-latency targets after stable playback.
- Preserve the original-game audio goal: no EQ, bass boost, widening, normalization, or other enhancement DSP.

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
- Reworked Android audio output around a dedicated audio-priority writer, short startup pre-buffer, underrun recovery, and clean speed-transition flushing to reduce static/crackle without blocking the emulator frame loop.

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

### Audio distortion hardening
- Replaced Retra's final mGBA-to-Android utility sinc conversion with a normalized 16-tap/1024-phase band-limited converter.
- Added explicit PCM16 saturation to prevent peak overflow/wrap distortion.
- Added anti-alias filtering for downsampling and a bit-transparent exact-rate bypass.
- Added regression coverage for resampler normalization, clipping safety, and exact-rate behavior.

## Scroll smoothness / responsiveness (10/10 pass)
- Added passive native-scroll coordination and scroll-idle background scheduling.
- Reduced Android WebView scroll-time blur/raster work.
- Expanded off-screen list/card rendering containment and earlier Home virtualization.
- Added lazy/async low-priority statistics artwork decoding.
