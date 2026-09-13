# Retra v1.0.2 — Automatic Recovery Protection

Retra v1.0.2 now treats Google Drive as an optional automatic recovery layer rather than only a manual sync target.

## Automatic protection

When Drive backup is enabled, meaningful portable-data changes are debounced and synchronized in the background. Repeated save/settings events collapse into one transfer, and a change that arrives while a sync is already running schedules one final pass instead of being dropped.

Protected portable data includes saves, save states, cheats, layouts, artwork, library identity metadata, favorites/categories, play history/statistics, and portable Retra settings. ROM and BIOS files are never uploaded.

A final urgent background sync is requested when Retra leaves the foreground so settings-only sessions are not left waiting for another gameplay event.

## Reinstall recovery

Data and Storage now includes **Restore from Google Drive**. The user selects the Google account containing the previous Retra data. That explicit recovery pass is remote-first for files that have no device sync journal yet.

This prevents a clean installation's newly generated empty `Metadata/library.json` or `Metadata/settings.json` from overwriting an older real cloud copy just because the empty files have newer timestamps.

After cloud files download, Retra reapplies portable library/settings metadata into Room and Preferences DataStore, refreshes the Web UI, and recreates missing-ROM placeholders. Save states/statistics remain visible while the ROM is absent; importing the matching ROM later reconnects the existing identity by hash.

## Android Auto Backup

The Android backup rules now opt in only portable Retra state (`persistent_data`, Preferences DataStore, Room database, and the legacy metadata preference file). Managed ROM content and transient emulation working files remain excluded. This reduces unnecessary backup payload size and makes the system backup layer more recovery-focused.

## Important limitation

Android still controls uninstall deletion and Auto Backup scheduling. Retra cannot secretly preserve its private storage after uninstall. The strongest recovery path is automatic Drive backup when enabled, backed by Android Auto Backup / Keep app data and manual `.retra` exports.
