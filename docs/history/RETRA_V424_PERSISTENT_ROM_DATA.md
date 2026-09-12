# Retra v4.24 — Persistent ROM Data

Retra now treats the ROM file as replaceable and the Retra `romId` as the permanent owner of user data.

## Identity

- Existing v4.23 UUIDs are preserved during migration.
- New imports receive an immutable UUID.
- SHA-256 is stored separately and is used to detect an exact ROM re-import or duplicate.
- ZIP / mGBA bundles hash the playable ROM payload (and patch when applicable), not just the outer archive.
- Renaming or moving the same ROM does not create a new game identity.

## Remove vs delete

**Remove from Library** archives the ROM record and keeps the permanent ID, save, save states, cheats, backups, categories, favourite metadata and other existing UI metadata.

**Delete Game Data** is a separate destructive action. It removes persistent save/state/cheat/config/layout/backup data and playtime, and writes a deletion tombstone so an interrupted legacy migration cannot restore intentionally deleted data.

## Persistent storage

Internal canonical data is organized as:

```text
persistent_data/
├── Saves/<romId>/game.sav
├── SaveStates/<romId>/
├── Cheats/<romId>/
├── Config/<romId>/
├── Layouts/<romId>/
├── Backups/<romId>/
└── Metadata/
```

mGBA receives an explicit working save path instead of deriving a `.sav` filename from the ROM filename. Retra commits that working save to the canonical `Saves/<romId>/game.sav` using verified temporary files and replacement semantics. Changed battery saves rotate up to five automatic backups.

## Legacy migration

On the first launch after the update Retra:

1. Keeps every existing UUID as its `romId`.
2. Copies legacy battery saves and save states into the persistent layout.
3. Verifies legacy copies before marking migration complete.
4. Hashes available ROMs and registers them in the SQLite identity index.
5. Preserves data for missing/unmatched ROMs under the old UUID for later reconnection.

Migration is retry-safe. Legacy data is not deleted as part of the migration.

## Re-import

```text
Import ROM
→ hash playable content
→ find existing hash
→ reuse romId
→ unarchive if necessary
→ update managed ROM/source location
→ leave user data untouched
```

An active duplicate does not create another Library entry. An archived exact match restores the previous Library identity and its associated data.

## Save-state safety

New save states include metadata containing `romId`, ROM content hash, platform, core version, slot and creation time. Retra rejects a state whose permanent ROM identity/hash no longer matches. Legacy states without metadata remain loadable.

## Missing ROMs

A missing ROM marks the file unavailable but keeps its database identity and user data. **Locate ROM** reconnects a known game only when its stored hash matches the selected ROM when a prior hash is available.

## Open App Folder

Settings → Open App Folder uses Android's Storage Access Framework and creates/exports the Retra data layout (`Saves`, `SaveStates`, `Cheats`, `Config`, `Layouts`, `Backups`, `Metadata`). Losing SAF permission does not delete Retra's internal canonical data.

## Core rule

```text
ROM file = replaceable
romId = permanent
SHA-256 = reconnect matcher
user data = permanent until Delete Game Data is explicitly confirmed
```
