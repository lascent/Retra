# Retra v4.28 — Pokémon ROM-Hack Flash 1M Fix

## What was actually wrong

The v4.27 source still forced `GBA_SAVEDATA_AUTODETECT` whenever **Cartridge save type = Automatic**. That can overwrite mGBA's own per-cartridge/Pokémon ROM-hack override after the ROM is loaded. mGBA's Pokémon hack handling can select `FLASH1M` (128 KiB / 1 Mbit) and RTC; resetting it to generic autodetect can make a hack see the wrong flash chip and show **Flash memory not detected**.

## Fix

- Automatic no longer calls `GBASavedataForceType(...AUTODETECT)`.
- Existing mGBA cartridge/ROM-hack detection is preserved.
- Manual EEPROM, SRAM, Flash 64K, Flash 128K and None still force the requested save type.
- Added a conservative fallback for Pokémon-derived GBA hacks using known Pokémon game-code families, the `FLASH1M_V` marker, or a Pokémon GBA header title.
- Reset now reapplies the safe handler, so manual choices remain stable without destroying Automatic detection.
- `.mgba` raw ROM/bundle handling is unchanged.

## ZIP comparison

`2(1)(3).zip` and the uploaded v4.24 project use the same native cartridge-save implementation. The v4.27 package also still contained the same Automatic→AUTODETECT behavior, which is why the prior fix did not resolve the user's SoulGold ROM-hack error.
