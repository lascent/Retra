# Retra v1.0.0 Release Checklist

Use this checklist before publishing the final APK/AAB or creating the `v1.0.0` Git tag.

## Automated checks

- [ ] `node --test tests/*.test.cjs` passes with every regression test green.
- [ ] Every JavaScript asset passes `node --check`.
- [ ] `python3 tools/validate_release.py` passes.
- [ ] GitHub Actions is green.
- [ ] Android/Kotlin compile and Android lint checks pass in CI.
- [ ] A clean release APK/AAB builds with the exact validated mGBA source revision.

## Install / upgrade

- [ ] Fresh install launches without crash or blank screen.
- [ ] Upgrade from the previous Retra build preserves library metadata and settings.
- [ ] App icon and launcher entry display correctly.
- [ ] Home -> other page -> Home returns immediately without a black/blank reload.

## ROM library and persistent data

- [ ] Import GB, GBC, and GBA ROMs.
- [ ] Re-importing the same ROM does not create duplicate persistent data.
- [ ] Remove a ROM from Library, import it again, and verify saves/states/categories/favourite/statistics reconnect.
- [ ] Rename or move a ROM and verify identity recovery by content hash.
- [ ] Open App Folder reuses the existing Retra folder tree without creating `Saves (1)`, `Saves (2)`, etc.

## Saves and emulation

- [ ] Battery save survives game close and app restart.
- [ ] Quick Save and Quick Load work from gameplay controls.
- [ ] Manual save states create/load/delete correctly.
- [ ] Auto-save/auto-load works after closing and reopening a game.
- [ ] Pokémon/Flash 1M titles save correctly.
- [ ] 0.2x/0.5x/1x/2x/4x/8x/16x speed modes behave correctly.

## Settings smoke test

- [ ] Appearance, theme, pure black, Translucent mode, and font persist after restart.
- [ ] Color Style affects game rendering only and persists.
- [ ] Stretch/filtering/frame skip/hardware rendering/shaders apply during gameplay.
- [ ] Sound, volume, and audio frequency apply and persist.
- [ ] Orientation/fullscreen/immersive mode apply without trapping the UI.
- [ ] BIOS enable/boot/file selection work on supported games.
- [ ] Cheats and ROM patches apply and can be disabled cleanly.
- [ ] Reset advanced settings returns only the intended settings to defaults.

## Controller / Screen Editor

- [ ] Every controller can be dragged, resized, and deleted where allowed.
- [ ] Adaptive resize handle remains accessible at all screen edges/corners.
- [ ] Controller scale shown in Screen Editor matches gameplay size.
- [ ] D-pad, A/B, L/R, Start/Select, Menu, Fast Forward, Quick Save, Quick Load, and Screenshot icons match between editor and gameplay.
- [ ] Added controls disappear from the Add Controllers list until removed.
- [ ] Portrait and landscape layouts remain independent and persist.

## Multiplayer / external services

- [ ] GBA Local Link works on supported titles.
- [ ] Wi-Fi Remote Link connects, disconnects, and recovers from desync cleanly.
- [ ] Bluetooth Remote Link works on at least two supported Android devices.
- [ ] Google Drive authorization and sync work, including a conflict/backup case.
- [ ] Automatic artwork respects Wi-Fi-only mode and manual artwork precedence.

## Final package

- [ ] `versionName` is `1.0.0` and intended `versionCode` is retained.
- [ ] README and release notes identify `Retra v1.0.0` as the stable release.
- [ ] `LICENSE` and `THIRD_PARTY_NOTICES.md` are included.
- [ ] Release APK/AAB is signed with the production key.
- [ ] Release notes list known limitations (for example unsupported GBA Single-Pak/Multiboot and RFU if still unsupported).
- [ ] Tag the exact tested commit as `v1.0.0` only after the device smoke test passes.


## v1.0.0 quality gates

- [ ] UI polish: no clipped text, inconsistent modal spacing, sticky touch-hover states or inaccessible focus states on tested phone sizes.
- [ ] Screen Editor: drag/resize remains responsive at edges, on rotation and during rapid pointer movement; keyboard arrows/+/−/Delete also behave correctly.
- [ ] Performance: no preference/database/file reads are introduced into the per-frame emulation hot path; network workers remain bounded.
- [ ] Multiplayer: connection quality, RTT/jitter/delay status, timeout/disconnect and desync recovery are exercised on both Wi-Fi and Bluetooth.
- [ ] Publish readiness: `CHANGELOG.md`, `SECURITY.md`, `CONTRIBUTING.md`, `LICENSE`, third-party notices, release notes and CI are present and current.
