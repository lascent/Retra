# Retra v1.0 Data and Storage backup

Retra now exposes **Data and Storage** from the More panel, directly below Color Style.

## Create backup

The user can independently include:

- Game saves
- Save states
- Cheats
- Library and metadata
- Controller layouts
- Artwork
- App settings

Pressing **Create backup** opens Android's system save picker with a suggested filename in the form `Retra_yyyyMMdd_HHmm.retra`, for example `Retra_20260913_0806.retra`. The user can save the file to any destination supported by Android DocumentsUI.

A `.retra` file is a ZIP-based Retra container with a versioned manifest. ROMs and BIOS files are deliberately not included.

## Restore backup

Restore validates the Retra manifest and completion marker before writing app data. Archive paths are restricted to known Retra categories, traversal paths are rejected, and both entry count and expanded size are bounded. Device-specific permissions, provider URIs, cloud-account identity, local BIOS paths, and migration flags are never restored as portable settings.

Restores merge selected content into Retra's managed storage. Library metadata is applied only to ROM identities already known to the current Retra installation.
