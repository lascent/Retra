# Retra v4.27 — mGBA ROM-hack save autodetect fix

## Problem
A Pokémon/SoulGold-style GBA ROM hack imported as `.mgba` could boot but show a flash-memory-not-detected / 1M flash error. The v4.25 compatibility change made `Automatic` stop calling mGBA's native savedata autodetection and instead relied on the ROM containing a `FLASH1M_V` marker. Modified hacks do not always preserve that marker, so the fallback was incomplete.

## Fix
Retra now restores the native behavior verified in the older working Retra build:

- `Automatic` maps to `GBA_SAVEDATA_AUTODETECT`.
- Retra calls `GBASavedataForceType(...AUTODETECT)` after the ROM is loaded.
- The save type is re-applied after `core->reset()` so mGBA can detect the patched/modified cartridge image in its final loaded state.
- Manual EEPROM, SRAM, Flash 64K, Flash 128K, and None overrides remain unchanged.
- The fragile `FLASH1M_V`-only fallback has been removed.

This change affects only GBA cartridge savedata selection. Retra v4.26 library multi-select responsiveness and UI behavior are preserved.
