# Retra v1.0.0 — First Stable Release

Retra v1.0.0 is the first stable public release of the Retra Android emulator project.

Retra is built around mGBA and focuses primarily on a polished GBA experience while retaining GB/GBC compatibility exposed through the integrated core.

## Highlights

- Persistent ROM identity so compatible Retra data can reconnect after a ROM is renamed, moved, removed, or re-imported.
- Battery saves, save states, automatic resume, cheats, ROM patching, BIOS support, per-ROM data organization, statistics, and artwork.
- Responsive Screen Editor with independent portrait/landscape layouts, controller add/remove/resize, edge-aware resize handles, and resizable emulator screen.
- GBA Local Link, Wi-Fi Remote Link, and Bluetooth Remote Link for supported normal Link Cable flows.
- Adaptive 60/90/120 Hz interface presentation without changing native game timing.
- Fast-forward and slow-motion modes with synchronized runtime/audio handling.
- Appearance themes, Color Style presets, translucent UI mode, controller opacity, and optional GLSL shaders.
- Google Drive sync plus Android folder fallback.
- Automatic artwork with persistent manual overrides.
- Modular Android/WebView architecture with regression tests, release validation, lint checks, and CI quality gates.

## Multiplayer scope

Retra v1.0.0 supports normal GBA Link Cable-style multiplayer through:

- Local Link on one Android device;
- Wi-Fi Remote Link;
- Bluetooth Remote Link.

Remote Link includes heartbeat/timeouts, smoothed RTT, jitter-aware adaptive input delay, input validation, state-hash checks, host-authoritative snapshots, bounded recovery retries, and deterministic input reseeding.

### Not supported in v1.0.0

- GBA Single-Pak/Multiboot
- GBA Wireless Adapter/RFU emulation

## Performance and UI

- UI refresh policy adapts between 60/90/120 Hz based on device capability and runtime constraints.
- GBA emulation timing remains independent from high-refresh UI presentation.
- WebView stays warm while gameplay is active to reduce slow or blank returns to Home.
- Large libraries use rendering containment/off-screen visibility optimizations.
- Frame-skip state is cached outside the emulation hot path.
- Network/background work is bounded instead of using unbounded worker growth.
- Screen Editor pointer movement is coalesced to one visual update per display frame.
- Touch targets, focus states, reduced-motion behavior, notices, and modal sizing are more consistent.

## Persistent data and recovery

- ROM identity uses stable content hashes and permanent `romId` records.
- Removing a ROM from the Library does not automatically destroy its persistent user data.
- Re-importing compatible ROM content can reconnect saves, states, layouts, artwork, categories, favourites, and statistics.
- Imports use a durable recovery journal.
- Save commits use verified temporary files, per-ROM locking, and rotating backups.

## Release packaging

- Public version: `1.0.0`
- Android `versionCode`: `447` (retained for upgrade compatibility)
- Minimum Android API: `26`
- Native toolchain: NDK r28 with 16 KB page-size compatibility settings
- License: MPL-2.0

## Build note

Retra's source package does not bundle a commercial ROM library or BIOS files. A full native APK/AAB build also requires a compatible validated mGBA source checkout under `third_party/mgba`, the legacy supported source location, or an explicitly configured `RETRA_MGBA_SOURCE_DIR`.

Before publishing a signed APK/AAB, complete [`RELEASE_CHECKLIST_v1.0.0.md`](RELEASE_CHECKLIST_v1.0.0.md) on physical devices and verify that CI/release gates are green for the exact commit being tagged.
