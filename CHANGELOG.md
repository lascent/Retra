## v1.0.3

- Local GBA Single-Pak / Multiboot support.
- Added two-player Local Single-Pak / Multiboot using one cartridge-host core and one BIOS-only receiving GBA core.
- Player 2 boots from a user-selected 16 KiB GBA BIOS with no cartridge attached, then joins the same mGBA lockstep SIO cable used by normal Local Link.
- Retra automatically holds Start + Select during the receiving GBA BIOS boot and releases them after startup so compatible games can enter their Single-Pak transfer flow.
- Normal Multi-Pak Local Link remains unchanged. Remote Link remains Multi-Pak-only, and GBA Wireless Adapter / RFU emulation is still not supported.

## v1.0.3 — Unified controller feedback
- Unified D-pad, A/B, L/R, Start/Select, combo/turbo, Menu, Screenshot, Quick Save/Load, and Speed controls on one controller-feedback profile.
- D-pad rolling still gives feedback only on real direction changes, while rapid multi-touch feedback is rate-limited to stay smooth instead of buzzing or stacking loud clicks.

## v1.0.3 — Soft controller feedback tuning
- Action buttons keep a subtle tap feel, while rapid D-pad jitter and multi-touch chords are prevented from stacking harsh click sounds.

## v1.0.3 — Library title and options cleanup
- Capped Compact Grid ROM titles at two lines, including 5–6 items-per-row layouts, so long names stay compact and consistent.
- Merged the separate Library Sort and Display header controls into one icon-only Library Options button; Sort and Display remain available as tabs in the same sheet.

## v1.0.3 — Google Drive Kotlin compile hotfix
- Moved **Restore from Google Drive** into the Google Drive section directly below **Backup Now**, keeping local backup/restore actions separate.

- Fixed `CloudSyncCoordinator.kt` to construct `ClearTokenRequest` through the public `ClearTokenRequest.builder()` factory instead of directly invoking the inaccessible nested Builder constructor.
- Preserves the real Google Drive API backup/restore path and all v1.0.3 features.

## v1.0.3 — Inter default, language selector, dense-title wrapping

- Switched Retra's default interface font from Poppins to Inter while keeping Poppins, Manrope, and DM Sans selectable.
- Added a one-time migration so installs carrying the old implicit Poppins default move to Inter without repeatedly overriding later user choices.
- Added Settings → Language directly below Fonts with English, Tiếng Việt, and Bahasa Indonesia.
- Added persistent Vietnamese and Indonesian localization for the main Web UI and core native gameplay-menu actions; language is stored in portable `ui_language` settings so backup/restore preserves it.
- Compact Library grid titles now wrap downward for long ROM names instead of being forced into a single ellipsis line, with denser typography at 5–6 items per row.
- Preserved ROM/user-provided titles and category data from automatic translation.

## v1.0.3 — Real Google Drive API backup/restore

- Replaced Google Drive folder-picker backup/restore with Google Identity Services authorization and Drive REST API v3.
- Cloud snapshots now live in `My Drive/Retra Backups`, with exact-folder reuse and automatic creation when needed.
- Added immediate Backup Now, direct Drive restore listing/download, transfer progress, connected-account status, last-success timestamps, and clearer errors.
- Added pre-upload/pre-restore backup validation and immutable cloud snapshots so a failed upload cannot replace the previous good backup.
- Added fresh-install protection: Retra checks cloud snapshots before allowing an empty local installation to create a cloud backup.
- Kept Android's document/folder pickers only for local backup/export/import flows.
- Added `docs/GOOGLE_DRIVE_SETUP.md` with required Google Cloud, OAuth, package-name, and signing-certificate setup.

## v1.0.3 — ROM title editing and storage polish

- Added **Edit name** to the ROM detail three-dot menu beside Change background and Change Cover.
- ROM title changes persist in the native Room library index and legacy title metadata without renaming the ROM file.
- Renamed titles stay consistent across Library, ROM details, History, backup/restore, and Google Drive sync.
- Includes the v1.0.2 controller 10/10, 105% landscape layout, rewind, save-import, and Google Drive restore fixes.
- Bumped Android release metadata to `versionName 1.0.3` / `versionCode 450`.

## v1.0.2 — All-ROM Smooth Consistent-Speed Hotfix
- Fast-forward timing now uses a shared exact emulated-time contract: 2×/4×/8×/16× target the same multiplier for every ROM relative to its own 1× timing.
- Turbo renderer work is budgeted immediately to the useful 60–120 Hz presentation range (for example 8× uses renderer frameskip 3 at a 120 Hz budget), while CPU/timers/input/game logic still execute every emulated frame.

- Fixed adaptive turbo fallback halving fresh visual states by doubling native batches.
- 4x/8x/16x now keep their normal short batches even when throughput protection is active.
- 8x/16x keep fractional high-refresh VSync synchronization during quality fallback instead of dropping to ~60 fresh states/s.
- Throughput fallback now requires about one second below 88% of target, preventing brief Android scheduling jitter from degrading light ROMs such as FireRed.
- Recovery is faster once throughput returns to at least 96% of target.
- Automatic renderer frameskip is much milder and reserved for sustained severe misses; turbo audio is muted only at deeper utilization drops.
- Selected 2x/4x/8x/16x emulation clocks are unchanged.

## v1.0.2 — All-ROM Consistent Fast-Forward
- Uses one cumulative timing governor for 2x, 4x, 8x and 16x across normal ROMs and ROM hacks.
- Adds measured throughput adaptation at every turbo multiplier.
- Heavy ROMs can temporarily use larger native batches (up to 16 frames) and adaptive renderer skipping before speed is sacrificed.
- Preserves turbo audio unless a severe measured throughput miss threatens the selected multiplier.
- Retains 8x/16x high-refresh VSync-aligned presentation.

### Turbo video + audio 10/10 pass
- Generalized fractional VSync-aligned batching to both 8x and 16x, allowing 8x to feed fresh states to 144/165 Hz panels while preserving the exact cumulative emulation-speed target.
- Publishes a completed turbo frame before audio/performance bookkeeping so ready images are less likely to miss the next VSync.
- Replaced turbo resample-then-average audio with one native speed-aware 32-tap band-limited FIR pass and low-latency AudioTrack output.
- Keeps 8x/16x audio enabled by default, coalesces small PCM packets, and only mutes/drains audio after a measured severe throughput miss to protect game speed.
- Added dedicated regression coverage for the new high-refresh and turbo-audio paths.

### My Boy-style 8×/16× throughput correction
- Reworked 8× to run 4 core frames per native batch and 16× to run 8, cutting JNI/locking overhead while keeping input sampled every emulated frame.
- Moved extreme-turbo PCM discard inside the same native batch so 8×/16× bypass the expensive 16-tap audio resampler/conditioner and avoid a second JNI hop.
- Explicitly disables mGBA video/audio sync for the direct-core frontend so Retra alone controls wall-clock pacing.
- Keeps Android presentation independent on continuous VSync, always displaying the newest completed frame instead of throttling emulation to screen refresh.
- Added real throughput monitoring; extra mGBA renderer frameskip is applied only when a ROM/device is measurably below the selected 8×/16× target.
- Added Android 12+ PerformanceHintManager workload hints for the long-lived mGBA worker.
- Full regression + release gate: 325/325 tests passing.

### True 16x turbo follow-up
- Rebuilt 8x/16x pacing around a cumulative throughput governor so 16x is not slowed by scheduler oversleep.
- Added 8-frame native 16x batches, an 8192-frame audio ring, and turbo-only mGBA renderer frameskip.
- Kept visible extreme-turbo presentation independent at a stable 60 Hz using the newest completed frame.
# Changelog

- Statistics → Playtime by ROM now uses each ROM’s persisted cover artwork and refreshes immediately after a cover change.
### Extreme 8×/16× turbo rendering hardening
- Batched each short turbo slice behind one native JNI call instead of crossing Kotlin/JNI once per hidden emulated frame.
- Kept input sampling on every emulated frame inside the native batch while converting only the final frame when a display update is actually due.
- Drains and speed-transforms mGBA audio once per safe short slice; the native 4096-frame ring and four-frame slice cap keep audio bounded while reducing resampler/JNI overhead.
- High turbo dynamically lowers the emulation worker's Android scheduler priority so the UI, RenderThread, GL thread, and audio writer are not starved by near-continuous 8×/16× CPU work.
- Removed turbo busy-spin precision waits at 4×+ so CPU time is available for presentation and audio instead of being burned between deadlines.
- On 120 Hz panels, 8×/16× now use a stable 60 Hz presentation cadence (an exact divisor of 120 Hz) while the core continues advancing at the requested turbo speed.
- Normal 1× emulation cadence and accuracy remain unchanged.

### Professional built-in shader pack
- Added nine curated GPU shader presets: GBA Color Corrected, Sharp, Smooth, Pixel Perfect, LCD Grid, LCD Response, Scanlines, CRT Lite, and Retro Warm.
- Added Low/Medium GPU-impact labels and per-preset descriptions in Video settings while keeping Off as the zero-cost default renderer.
- Preserved custom GLSL installation and automatic fallback to Off if a shader fails to compile.
- Cached OpenGL shader attribute/uniform locations and avoided redundant texture-filter state changes to reduce per-frame shader overhead.
- Kept shader rendering opt-in and presentation-bound so fast-forward hidden core frames do not waste GPU shader work.

### Screen Editor drag smoothness hardening
- Reworked live controller dragging so only the active control is moved per animation frame instead of recalculating every controller.
- Reworked direct screen dragging so the frame follows the finger without re-running full responsive layout/resize-handle geometry until release.
- Removed expensive selection shadows during active drag and kept persistence outside the live pointer loop for lighter, more professional movement.
- Renamed the visible **Better Fit** preset to **Best Fit** while preserving the internal `betterfit` key for existing saved layouts.

### Screen Editor responsive layout hardening
- Added a **Best Fit** screen-size preset matched to the supplied landscape reference: full usable height at the GBA 3:2 aspect with balanced controller zones.
- The emulator screen itself can now be dragged directly to reposition it; custom coordinates stay normalized so the layout remains responsive across device sizes.
- Screen dragging, edge resizing, and resize-handle scaling are coalesced to animation frames to reduce WebView jank.
- The screen resize handle now chooses an adaptive corner and keeps the opposite corner anchored for more natural expand/minimize behavior.

### Fast-forward smoothness hardening
- Reworked 2×/4×/8×/16× execution into short paced turbo slices instead of one large burst per normal GBA frame.
- Added a native no-video frame path so intermediate turbo frames skip JNI pixel conversion while still processing core input and audio.
- Caps Android framebuffer publication to the useful 60/90/120 Hz presentation cadence, reducing wasted work and improving frame pacing on high-refresh displays.
- Keeps normal-speed emulation timing unchanged.

## v1.0.2 — 2026-09-13 — Update checking and reinstall recovery

- Fixed GitHub Actions Android 17 provisioning by installing the published `platforms;android-37.0` SDK package into the runner SDK root while keeping app `compileSdk`/`targetSdk` at API 37.
- Hardened automatic Google Drive recovery: debounced/coalesced background protection, last-backup status, urgent foreground-exit flush, and an explicit Restore from Google Drive action.
- Added remote-first clean-reinstall recovery so freshly generated empty metadata cannot overwrite an existing cloud library/settings copy.
- Cloud downloads now rehydrate Room library/statistics and portable settings immediately, including missing-ROM placeholders.
- Narrowed Android Auto Backup/device-transfer rules to portable Retra data to reduce quota pressure.

- Added an in-app **Check for updates** action to both About entry points with current-version status and a Retra-styled update prompt.
- Added automatic background checks against the official `lascent/Retra` GitHub Releases feed with a six-hour success cooldown, bounded timeouts, and no gameplay-thread network work.
- Update actions open only official HTTPS GitHub release/APK URLs; Android still owns the user-confirmed installation flow.
- Enabled Android's `hasFragileUserData` uninstall prompt so supported Android builds can offer **Keep app data** when Retra is uninstalled.
- Retained Android Auto Backup/device-transfer support for app data while continuing to exclude managed ROM content and transient save working files from cloud/device backup payloads.
- Bumped Android release metadata to `versionName 1.0.2` / `versionCode 449`.

### Backup restore accuracy hardening
- Restored library entries now survive even when their ROM files are absent, retaining the original ROM identity, favorites, categories, playtime and save-state linkage.
- Missing ROMs appear as `ROM file required` placeholders and reconnect automatically to the same `romId` when the matching SHA-256 ROM is imported.
- Portable play-history metadata now restores Started/Recently Played statistics and History alongside save/battery-state counts.
- Restored global settings and per-ROM `Config` data continue to use the existing portable settings/config restore path.

### v1.0.1 performance hardening
- Triple-buffered gameplay presentation keeps Bitmap/GL upload work off the emulator frame lock.
- High-frequency range previews persist preferences only on final commit.
- Serialized storage maintenance now runs at Android background priority during gameplay.
- Added dedicated regression coverage and performance-hardening documentation.

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
- Fixed built-in shader selection on devices that destroy hidden GLSurfaceView contexts: presets now compile lazily when gameplay is visible, retry with a GLES2 compatibility path, and reserve file import for custom shaders only.

### 16x frame-pacing refinement
- Added display-synchronized fractional 16x batching to remove fixed-batch beat/judder on 120/144/165 Hz panels without changing emulation speed.
- Added VSync phase anchoring, adaptive renderer fallback/recovery, and high-refresh extreme-turbo mode selection.

