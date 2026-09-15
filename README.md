<div align="center">

# Retra

**A modern open-source Android emulator built around mGBA, focused on a polished Game Boy Advance experience.**

[![Release](https://img.shields.io/badge/release-v1.0.3-blue)](https://github.com/lascent/Retra/releases/tag/v1.0.3)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/lascent/Retra/releases)
[![License](https://img.shields.io/badge/license-MPL--2.0-blue.svg)](LICENSE)
[![Core](https://img.shields.io/badge/core-mGBA-8A2BE2)](https://mgba.io/)

[**Download latest release**](https://github.com/lascent/Retra/releases) ·
[**Changelog**](CHANGELOG.md) ·
[**Troubleshooting**](TROUBLESHOOTING.md) ·
[**Contributing**](CONTRIBUTING.md)

</div>

---

## About Retra

Retra is an open-source Android retro emulator built around the **mGBA** core, with feature development primarily focused on Game Boy Advance gameplay.

It combines emulation with a mobile-first interface, persistent game data, customizable controls, rewind, backup and restore tools, save states, cheats, artwork, display customization, localization, and local or remote GBA link features.

> [!IMPORTANT]
> Retra does **not** include commercial ROMs, BIOS files, or copyrighted game assets. Use only content you are legally permitted to use.

## Features

### Emulation & gameplay

- GB, GBC, and GBA emulation through the mGBA integration.
- Fast-forward and slow-motion speed controls.
- 5-second, 10-second, and 15-second rewind.
- Adaptive 60/90/120 Hz interface presentation while keeping emulation timing independent from display refresh rate.
- BIOS support, ROM patches, cheats, statistics, and per-ROM configuration.
- Optional GLSL shaders and gameplay Color Style presets.

### Saves & persistent data

- Battery saves, save states, and automatic resume.
- Per-ROM `.sav` import with compatibility checks and backup-before-replace protection.
- Persistent ROM identity based on SHA-256 content hashes.
- Compatible saves and metadata can reconnect after a ROM is moved, renamed, removed, or re-imported.
- Portable **Create Backup** and **Restore Backup** using `.retra` files.
- Real Google Drive API backup and restore.
- Automatic Google Drive backup to `My Drive/Retra Backups`.
- Android Auto Backup/device transfer support.
- Reinstall continuity through Android's **Keep app data** flow.

### Controls & interface

- Screen Editor with independent portrait and landscape layouts.
- Draggable and resizable on-screen controls.
- Optional Screen Editor gridlines.
- Emulator-screen resizing and edge-aware layout editing.
- Controller opacity controls.
<<<<<<< HEAD
- Optional controller tap sound under **Sound**.
=======
- Improved multi-touch and diagonal controller handling.
>>>>>>> 074f4fe45cb33e69bc1674b6c23fff9373090a12
- Automatic artwork with persistent manual cover and background overrides.
- ROM title editing.
- Library Sort and Display options.
- Compact Grid, Comfortable Grid, Cover-only Grid, and List modes.
- Adjustable Library items per row.
- Two-line ROM title layout for dense grids.
- Lightweight gameplay color presets: **Classic**, **Vivid**, **Warm**, and **Muted**.

### Appearance & language

- **Inter** is the default Retra font.
- English language support.
- Vietnamese language support.
- Indonesian language support.
- Language preferences persist across restarts and backups.

### Multiplayer & sync

- GBA **Local Link** on one Android device.
- **Wi-Fi Remote Link** between supported devices.
- **Bluetooth Remote Link** between supported Android devices.
- Google Drive backup and restore through the real Google Drive API.
- Conflict-safe cloud backup behavior.
- Local backup, export, and import continue to use Android's Storage Access Framework where appropriate.

### Updates & project quality

- Automatic GitHub Releases update checks on each fresh app process, so newly published versions are discovered on the next launch.
- Manual **Check for updates** action in About.
- Modular Android architecture.
- Release validation, regression tests, lint checks, and CI quality gates.

## Download

The latest stable APK and release notes are available from:

### **[GitHub Releases →](https://github.com/lascent/Retra/releases)**

Current stable release: **Retra v1.0.3**

| Item | Current status |
|---|---|
| Version | `v1.0.3` |
| Release channel | Stable |
| Minimum Android | API 26 / Android 8.0+ |
| License | Mozilla Public License 2.0 |
| Maintainer | Zense |
| Emulator core | mGBA |

## Release history

| Version | Summary |
|---|---|
| **v1.0.0** | Official release |
| **v1.0.1** | Added Create Backup and Restore Backup |
| **v1.0.2** | Added in-app update checking and Android reinstall-recovery hardening |
| **v1.0.3** | Added rewind, real Google Drive backup and restore, ROM title editing, `.sav` import, controller improvements, Library customization, localization, and UI refinements |

Public release numbering follows Semantic Versioning. Earlier project work is organized in [`docs/DEVELOPMENT_HISTORY.md`](docs/DEVELOPMENT_HISTORY.md).

## Color Style

Retra includes lightweight gameplay color presets that affect only the rendered game image. The Retra interface itself remains unchanged.

**Available presets:** Classic · Vivid · Warm · Muted

<p align="center">
  <img src="docs/images/color-style.jpg" alt="Retra Color Style screen showing Classic, Vivid, Warm, and Muted gameplay color presets" width="420">
</p>

## Multiplayer scope

Retra v1.0.3 supports normal GBA Link Cable-style multiplayer and two-player Local Single-Pak/Multiboot through:

- **Local Link** on one Android device.
- **Single-Pak / Multiboot** on one Android device using a user-provided 16 KiB GBA BIOS for the cartridge-less receiver.
- **Wi-Fi Remote Link** between supported devices.
- **Bluetooth Remote Link** between supported Android devices.

Remote Link includes connection health metrics, heartbeat/timeouts, state-hash validation, jitter-aware input delay, host-authoritative recovery, bounded recovery retries, and deterministic input reseeding.


## Persistent game data

Retra separates replaceable ROM files from persistent user data.

```text
ROM content -> SHA-256 -> permanent romId
                        |
                        +-- Saves
                        +-- Save states
                        +-- Cheats
                        +-- Layouts
                        +-- Covers/backgrounds
                        +-- Categories/favourites
                        +-- Statistics
