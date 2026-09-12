# Retra v4.01 — In-game Menu, Save States, Cheats & Reset

This update reapplies the in-game emulator menu system on top of the user's Retra v4.00 files without replacing the newer v4.00 changes.

## Added / Reapplied

- In-game menu: Load, Save, Fast forward, Cheats, Settings, Link remote, Link local, Screenshot, Reset, Close.
- Per-ROM save-state manager with Quick plus Slot 1 through Slot 10.
- Save-state thumbnails, saved date/time, empty-slot display, overwrite confirmation, and persistent per-ROM state files.
- Per-ROM cheat manager with add/edit/delete, enable/disable, cheat name, multiline code entry, and cheat type selection:
  - Auto detect
  - GameShark v1/v2
  - GameShark v3 (Action Replay)
  - Code Breaker
  - Raw code
- Native mGBA cheat bridge so enabled cheat entries are applied to the running core.
- Reset confirmation displayed over the paused game; reset does not delete saves, states, layouts, settings, or cheats.
- Game framebuffer screenshots saved without controller/menu overlays.
- In-game Settings handoff to the existing Retra Settings UI, with Back returning to the still-loaded game.
- Portrait/landscape resizing for the in-game menu.

## Preserved from v4.00

- Existing v4.00 changes and files were kept in place, including the newer controller/layout, quick-save/load icon, category/history, opacity, cover sizing, and fast-forward updates.

## Notes

- Link remote and Link local are included in the menu, but remain unavailable until a link-cable/network backend is added.
- The project still expects the existing external mGBA source folder configured by the current Android project.
