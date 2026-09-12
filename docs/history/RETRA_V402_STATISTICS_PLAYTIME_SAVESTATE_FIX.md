# Retra v4.02 — Statistics, ROM Playtime & Save State Fix

## Statistics
- Statistics now refresh from real library/native data when the Statistics page opens.
- Working counts: Library, GBA, GBC, GB, Started, Favorites, Recently played, Save states, Battery saves, Backups.
- Completed games reads the existing `rom.completed` value when present.

## Playtime
- Native Android gameplay time is tracked per ROM using elapsed real time while emulation is actually running.
- Time stops while the in-game menu is open, while Settings is open, and while the app is paused/backgrounded.
- Playtime persists in SharedPreferences and is shown as Total playtime plus Playtime by ROM.
- The ROM playtime list is sorted from most played to least played and displays the ROM cover/title/system and play count.

## Quick Save / Slots
- Reworked native mGBA save-state writing so it serializes to memory first, then writes the completed state file to Android storage.
- Uses full mGBA save-state flags and a raw-core-state fallback if optional serialization fails.
- Loading supports both normal serialized mGBA states and fallback raw states.
- Empty/zero-byte failed state files are cleaned up and no longer appear as valid slots.
- Applies to Quick plus Slot 1 through Slot 10.
