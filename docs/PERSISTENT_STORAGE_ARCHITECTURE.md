# Retra Persistent Storage Architecture

This document describes the production-oriented persistent-data architecture introduced after Retra v4.24 Release 9.5.

## Ownership model

```text
Room        -> ROM/library structured metadata
Filesystem  -> per-ROM user files
DataStore   -> application-wide preferences
romId       -> permanent key joining all of the above
```

`romId` is permanent. ROM file names, paths and provider URIs are replaceable locations, not identity.

## Room database

Retra keeps the existing database filename `retra_rom_identity.db` so current installs can migrate in place.

Database v2 stores the ROM record fields used by the Library and persistence layer, including:

- `rom_id`
- `content_hash`
- `hash_algorithm`
- `platform`
- `display_name`
- `file_name`
- `source_uri`
- `current_file_uri`
- `launch_path`
- `patch_path`
- `file_size`
- `last_modified`
- `favorite`
- `categories_json`
- `playtime_ms`
- `archived`
- `file_available`
- `final_content_hash`
- `legacy_identity_hash`
- timestamps

Migration `1 -> 2` uses `ALTER TABLE` and preserves the original `rom_records` rows. It does not drop the user's database. A second `rom_identity_aliases` table preserves older valid hashes that should still resolve to the same permanent `romId`.

The instrumentation suite includes a legacy-v1 database fixture which is opened through the Room v2 store and checked for identity/path/metadata preservation.

## ROM identity and fingerprint cache

Normal playable ROM identity is:

```text
ROM bytes -> SHA-256 -> resolve/create permanent romId
```

The fingerprint cache uses:

```text
currentFileUri + fileSize + lastModified -> previously verified SHA-256
```

The cache is only an optimization. If size or modified time changes, Retra hashes the contents again. SHA-256 remains authoritative.

## Patched ROM identity

When both a base ROM and patch are available, Retra materializes the final playable ROM through the mGBA core, hashes those final bytes, and uses that final SHA-256 as the authoritative content identity.

Compatibility aliases preserve the older Retra `bundle-v1|baseHash|patchHash` identity and pre-final-patch content hashes so an upgraded user does not receive a new `romId` solely because the identity algorithm improved.

## Filesystem layout

All per-ROM files use the permanent ID:

```text
persistent_data/
├── Saves/<romId>/
├── SaveStates/<romId>/
├── Cheats/<romId>/cheats.json
├── Config/<romId>/config.json
├── Layouts/<romId>/portrait.json
├── Layouts/<romId>/landscape.json
├── Covers/<romId>/cover.webp
├── Backgrounds/<romId>/background.webp
├── Backups/<romId>/
└── Metadata/
```

Removing a ROM from the Library archives its Room record and does not remove these directories. Only the explicit **Delete Game Data** action removes per-ROM persistent files.

Battery saves continue to use verified temporary writes, per-ROM synchronization, recovery of newer interrupted-session working saves, and rotating backups.

### Cheats

`Cheats/<romId>/cheats.json` is authoritative. The old SharedPreferences value is read only as a migration/downgrade compatibility source and is mirrored temporarily when a cheat file is changed.

### Config

The Android bridge exposes `getRomConfig(romId)` / `setRomConfig(romId, json)` backed by `Config/<romId>/config.json`, giving future ROM-specific emulator options a persistent store that is separate from DataStore.

### Controller and emulator-screen layouts

Portrait and landscape layouts are independent files under `Layouts/<romId>/`. Each bundle contains controller geometry plus emulator-screen geometry. Existing global portrait/landscape layouts migrate to `_default` and are used as fallback defaults for ROMs that do not yet have their own layout.

### Covers and backgrounds

User-selected covers and backgrounds are encoded as WebP and atomically replaced under the matching `romId`. The WebView reads them through Android's HTTPS `WebViewAssetLoader` origin rather than unrestricted `file://` access.

## Crash-safe import journal

Each import has an fsynced JSON journal in `filesDir/import_journal` and advances monotonically:

```text
PREPARING
  -> ROM_READY
  -> DATABASE_COMMITTED
  -> COMPLETE
```

On startup Retra inspects incomplete entries:

- `PREPARING`: roll back only transaction-owned files for brand-new records.
- `ROM_READY`: reconstruct/commit the Room record from verified files, then finish the journal.
- `DATABASE_COMMITTED`: mark complete and clear the journal.
- Unexpected recovery errors keep the journal for a future retry rather than deleting user data.

ROM/patch copies use staging, size/hash verification, fsync, and same-filesystem atomic replacement when supported.

## DataStore

Preferences DataStore is authoritative only for application-wide settings, including theme/font/UI preferences, orientation defaults, controller opacity, fast-forward defaults, sound/video/core options, BIOS preferences and cloud/app-folder settings.

Existing global SharedPreferences values are migrated into DataStore. ROM-specific legacy preference keys remain available only as compatibility sources while their authoritative data is moved into Room or the per-ROM filesystem.

## Compatibility rule

```text
ROM file = replaceable
romId    = permanent
user data = permanent until explicitly deleted
```

Existing users are migrated incrementally. Retra deliberately keeps selected one-version compatibility mirrors so an interrupted upgrade or short-term downgrade does not immediately strand user data.

## Validation

Dependency-free source/contract regression tests are under `tests/` and Android migration/identity tests are under `app/src/androidTest/`.

Before publishing an APK, run the Android Gradle build with a compatible mGBA checkout and device-test:

- legacy v1 DB -> Room v2 migration
- plain-ROM import/re-import/rename/move
- patched-ROM identity and legacy alias reconnection
- process termination during each import-journal phase
- battery-save recovery and backup rotation
- per-ROM portrait/landscape layouts
- cover/background persistence
- Library remove vs Delete Game Data
- Local Link / Remote Link
