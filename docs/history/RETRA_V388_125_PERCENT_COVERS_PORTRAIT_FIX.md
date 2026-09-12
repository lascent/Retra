# Retra v3.88 — 125% Controls, Cover Lookup, Portrait Layout Fix

## Screen Editor / Controller Layout
- Portrait and landscape remain fully separate saved datasets.
- Storage keys were bumped to v3.88 so older mixed/corrupted coordinates cannot leak into the new defaults.
- All built-in controls now default to **125%** in both portrait and landscape.
- Newly added controls also start at **125%**.
- Portrait uses deterministic My Boy!-style zones instead of measuring/reusing the other orientation's DOM positions:
  - emulator screen at the top
  - L / Menu / R below the screen
  - Start / Select in the lower middle
  - D-pad lower-left
  - A / B lower-right
- Landscape remains independent:
  - Menu top-center
  - L / R top corners
  - D-pad lower-left
  - A / B lower-right
  - Start / Select bottom-center
- Rotation swaps datasets only; it does not transform or overwrite controller coordinates.
- Native Android gameplay preferences were also bumped to v3.88 and use 125% defaults.
- The emulator screen can now be resized from its top, bottom, left, or right edge in the editor. Aspect ratio is preserved unless Break Aspect Ratio is active.

## ROM Covers
- Removed generated title artwork, so Retra no longer creates a fake cover containing the game title.
- If a ROM has no confidently matched artwork, it remains coverless.
- When online, Retra tries an exact-name cover lookup for GB/GBC/GBA titles using the Libretro thumbnail naming convention.
- No fuzzy match and no base-game fallback are used, which avoids assigning the wrong cover to ROM hacks.
- Successful automatic artwork is locally cached in the ROM metadata.
- Manually selected covers are marked as manual and are never overwritten by automatic lookup.
