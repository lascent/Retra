# Retra v4.25 — Flash 1M ROM-Hack Compatibility Fix

## Problem

Some Pokémon GBA ROM hacks could reach the title screen but then show **“The 1M sub-circuit board is not installed.”** Retra's `Automatic` cartridge-save option was calling `GBASavedataForceType(...AUTODETECT)` after mGBA had already selected a more specific per-game save type. That could erase mGBA's ROM-hack override and fall back to the wrong flash size.

## Fix

- `Automatic` now means **no Retra save-type force**. mGBA keeps control of its game database, ROM-hack detection, and normal save-memory autodetection.
- Explicit user choices still force EEPROM, SRAM, Flash 64K, Flash 128K, or None.
- In Automatic mode, Retra scans a loaded GBA ROM for the standard `FLASH1M_V` marker. If present and mGBA has not selected Flash 1M, Retra forces `GBA_SAVEDATA_FLASH1M` as a compatibility fallback.
- The fallback is reapplied after core reset so a reset cannot regress the save type.

This is intended for hacks such as Pokémon SoulGold and similar Emerald/FireRed-based projects that require 128 KiB Flash but have altered headers or identifiers.

## Validation

- Added regression coverage ensuring Automatic does not force `GBA_SAVEDATA_AUTODETECT`.
- Added regression coverage for the `FLASH1M_V` fallback.
- Existing Home navigation, persistent storage, save/resume, category, selection, and release-hardening tests remain enabled.
