# Retra v1.0.0 — Built-in App Folder / DocumentsProvider

Retra's **Open app folder** action now behaves like emulator apps that expose their
own location in Android Files.

## Behavior

- Retra always stores its managed data in the app-owned `persistent_data` root.
- A `RetraDocumentsProvider` exposes that exact root to Android's Files/Documents UI.
- Android Files can show **Retra** as its own storage location.
- **Open app folder** uses `ACTION_VIEW` with the provider root URI and root MIME
  type to open the Retra root directly.
- No `ACTION_OPEN_DOCUMENT_TREE` is used by **Open app folder**, so there is no
  **Use this folder** confirmation and no folder permission that must be renewed.
- Saves, save states, cheats, configuration, layouts, backups, artwork metadata and
  the portable settings snapshot remain in Retra-owned storage and are visible
  through the provider.
- Existing explicit import/export and Google Drive folder workflows continue to use
  SAF where user-selected external storage is actually required.

## Provider root

Authority: `com.retra.emulator.documents`

Visible folders include:

- `Saves`
- `SaveStates`
- `Cheats`
- `Config`
- `Layouts`
- `Covers`
- `Backgrounds`
- `Backups`
- `Metadata`

The provider validates canonical paths before opening files so document IDs cannot
escape Retra's managed data root.

## Direct-open and storage summary fix

`Open app folder` now targets `DocumentsContract.buildRootUri(AUTHORITY, ROOT_ID)` with
`DocumentsContract.Root.MIME_TYPE_ITEM`. This is the preferred DocumentsUI root route.
For OEM file managers that do not accept that route, Retra also tries the provider root
document with the standard directory MIME type. Retra starts both routes directly instead
of relying on `PackageManager.resolveActivity()`, because package visibility on some Android
builds can hide a valid Files handler during preflight resolution.

The Retra root subtitle is generated from `StatFs(...).availableBytes`, converted to decimal GB and rounded to the nearest whole GB, for example:
`Retra storage • 162 GB free`.
