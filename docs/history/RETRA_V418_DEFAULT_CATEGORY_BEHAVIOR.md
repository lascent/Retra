# Retra v4.18 — Default Category Behavior

## Changes
- Renamed the built-in Library chip from `All` to `Default` while keeping it as the main view that shows every ROM.
- New ROMs now start in the main `Default` Library view with no custom category assignment.
- `Set as default` in the Filter three-dot menu now remembers the current category selection for that specific ROM.
- `Reset` restores that ROM to its own saved default category selection immediately.
- If a ROM has no saved custom default, Reset returns it to the main `Default` Library state (no custom categories).
- Set as default and Reset update the ROM assignment immediately, so the category chips and filtering stay in sync.
- Deleting a custom category also removes it from any saved per-ROM default snapshots.
- `Default` and `All` are reserved names and cannot be created as custom categories.
