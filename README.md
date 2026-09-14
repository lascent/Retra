<div align="center">

# Retra

**A modern open-source Android emulator built around mGBA, focused on a polished Game Boy Advance experience.**

[![Release](https://img.shields.io/github/v/release/lascent/Retra?label=release)](https://github.com/lascent/Retra/releases)
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

It combines emulation with a mobile-first interface, persistent game data, customizable controls, backup and restore tools, save states, cheats, artwork, display customization, and local or remote GBA link features.

> [!IMPORTANT]
> Retra does **not** include commercial ROMs, BIOS files, or copyrighted game assets. Use only content you are legally permitted to use.

## Features

### Emulation & gameplay

- GB, GBC, and GBA emulation through the mGBA integration.
- Fast-forward and slow-motion speed controls.
- Adaptive 60/90/120 Hz interface presentation while keeping emulation timing independent from display refresh rate.
- BIOS support, ROM patches, cheats, statistics, and per-ROM configuration.
- Optional GLSL shaders and gameplay Color Style presets.

### Saves & persistent data

- Battery saves, save states, and automatic resume.
- Persistent ROM identity based on SHA-256 content hashes.
- Compatible saves and metadata can reconnect after a ROM is moved, renamed, removed, or re-imported.
- Portable **Create Backup** and **Restore Backup** using `.retra` files.
- Android Auto Backup/device transfer support.
- Reinstall continuity through Android's **Keep app data** flow.

### Controls & interface

- Screen Editor with independent portrait and landscape layouts.
- Draggable and resizable on-screen controls.
- Emulator-screen resizing and edge-aware layout editing.
- Controller opacity controls.
- Automatic artwork with persistent manual cover and background overrides.
- Lightweight gameplay color presets: **Classic**, **Vivid**, **Warm**, and **Muted**.

### Multiplayer & sync

- GBA **Local Link** on one Android device.
- **Wi-Fi Remote Link** between supported devices.
- **Bluetooth Remote Link** between supported Android devices.
- Google Drive sync with conflict-safe behavior.
- Android Storage Access Framework folder fallback.

### Updates & project quality

- Automatic GitHub Releases update checks.
- Manual **Check for updates** action in About.
- Modular Android architecture.
- Release validation, regression tests, lint checks, and CI quality gates.

## Download

The latest stable APK and release notes are available from:

### **[GitHub Releases →](https://github.com/lascent/Retra/releases)**

Current stable release: **Retra v1.0.2**

| Item | Current status |
|---|---|
| Version | `v1.0.2` |
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

Public release numbering follows Semantic Versioning. Earlier project work is organized in [`docs/DEVELOPMENT_HISTORY.md`](docs/DEVELOPMENT_HISTORY.md).

## Color Style

Retra includes lightweight gameplay color presets that affect only the rendered game image. The Retra interface itself remains unchanged.

**Available presets:** Classic · Vivid · Warm · Muted

<p align="center">
  <img src="docs/images/color-style.jpg" alt="Retra Color Style screen showing Classic, Vivid, Warm, and Muted gameplay color presets" width="420">
</p>

## Multiplayer scope

Retra v1.0.2 supports normal GBA Link Cable-style multiplayer through:

- **Local Link** on one Android device.
- **Wi-Fi Remote Link** between supported devices.
- **Bluetooth Remote Link** between supported Android devices.

Remote Link includes connection health metrics, heartbeat/timeouts, state-hash validation, jitter-aware input delay, host-authoritative recovery, bounded recovery retries, and deterministic input reseeding.

### Not supported in v1.0.2

- GBA Single-Pak / Multiboot
- GBA Wireless Adapter / RFU emulation

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
```

Structured ROM metadata is stored with Room, application-wide settings use Preferences DataStore, and save commits use verified temporary files, per-ROM locking, and rotating backups.

Import recovery uses a durable journal so interrupted imports can be recovered safely.

For the full storage design, see [`docs/PERSISTENT_STORAGE_ARCHITECTURE.md`](docs/PERSISTENT_STORAGE_ARCHITECTURE.md).

## Build from source

### Requirements

- Android Studio with the Android SDK required by the Gradle project.
- The JDK/toolchain required by the included Android Gradle Plugin.
- Android NDK **r28**.
- CMake **3.22.1**.
- The exact locked mGBA source revision used by Retra.

Retra is pinned to mGBA commit:

```text
543a197582c30364584d773a974d7f991892fa43
```

The revision is recorded in `third_party/mgba.lock`.

### Prepare mGBA

```bash
python3 tools/prepare_mgba.py
```

This prepares `third_party/mgba/` at the exact locked commit.

CMake also accepts the legacy sibling `../mgba/` location or an explicit `RETRA_MGBA_SOURCE_DIR`, but verifies that the source matches the clean locked revision before configuring the native build.

### Android release metadata

```text
versionName = 1.0.2
versionCode = 449
minSdk      = 26
```

`versionCode 449` upgrades cleanly over v1.0.1 (`448`) and v1.0.0 (`447`).

### Open and build

1. Clone or extract the Retra repository.
2. Run `python3 tools/prepare_mgba.py`.
3. Open the repository root in Android Studio.
4. Allow Gradle to sync and install the requested SDK, NDK, and CMake components.
5. Build and run on an Android device.

### Command-line validation

**Windows**

```bat
gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

**macOS / Linux**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

A full APK/AAB build requires the exact mGBA revision recorded in `third_party/mgba.lock`. CMake rejects a different or dirty tracked checkout.

## Repository structure

```text
Retra/
├─ app/                         Android application
│  └─ src/main/
│     ├─ assets/retra/          WebView UI modules, styles, and branding
│     ├─ cpp/                   Native mGBA bridge and native build config
│     └─ java/.../emulator/     Android controllers, repositories, and runtime
├─ docs/                        Release, architecture, and engineering docs
│  └─ history/                  Archived implementation/build notes
├─ tests/                       Dependency-light regression tests
├─ third_party/                 Preferred project-local mGBA location
├─ tools/                       Release validation scripts
├─ CHANGELOG.md                 Public release and development history
├─ TROUBLESHOOTING.md           User troubleshooting and bug-reporting guide
├─ CONTRIBUTING.md              Contribution guide
├─ SECURITY.md                  Security policy
├─ THIRD_PARTY_NOTICES.md       Third-party licensing notices
└─ LICENSE                      MPL-2.0
```

See [`docs/README.md`](docs/README.md) for the documentation index.

## Validation

Run the dependency-light release gate before publishing changes:

```bash
python3 tools/release_gate.py
```

It validates JavaScript syntax, regression tests, release structure, and release metadata that do not require the Android SDK/NDK or mGBA.

For Android-specific validation, also run the Gradle compile/test/lint tasks above.

Before publishing an APK/AAB, complete the physical-device checks in:

- [`docs/RELEASE_CHECKLIST_v1.0.0.md`](docs/RELEASE_CHECKLIST_v1.0.0.md)
- [`docs/REAL_DEVICE_VALIDATION_MATRIX_v1.0.1.md`](docs/REAL_DEVICE_VALIDATION_MATRIX_v1.0.1.md)

## Documentation & support

- [`CHANGELOG.md`](CHANGELOG.md) — public release and development history.
- [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — installation, ROM loading, performance, audio, controls, saves, backups, BIOS, multiplayer, sync, and crash-reporting help.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — contribution requirements.
- [`SECURITY.md`](SECURITY.md) — vulnerability reporting and security boundaries.
- [`docs/DEVELOPMENT_HISTORY.md`](docs/DEVELOPMENT_HISTORY.md) — organized pre-v1.0 development history.
- [`docs/PERSISTENT_STORAGE_ARCHITECTURE.md`](docs/PERSISTENT_STORAGE_ARCHITECTURE.md) — persistent storage design.

## License

Retra source code is released under the **Mozilla Public License 2.0 (MPL-2.0)**. See [`LICENSE`](LICENSE).

Retra integrates with the separately maintained **mGBA** emulator core. mGBA is not owned by Retra and retains its own copyright and MPL-2.0 notices.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) and the license files included with the exact mGBA revision used for your build.

ROMs, BIOS files, game artwork, trademarks, and game content are not distributed with Retra and remain the property of their respective rights holders.

---

<div align="center">

**Retra** · Android retro emulation powered by mGBA

</div>
