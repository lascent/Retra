# Retra v4.30 — Optimized Automatic Artwork

Retra can now look up ROM cover art automatically without putting network or image work on the UI thread.

## Behavior

- ROM cards appear immediately; artwork never blocks import or Library rendering.
- Missing covers use a stable per-ROM gradient fallback so cards never look blank.
- Exact-title artwork is requested from the Libretro thumbnail repository for GB/GBC/GBA (raw `.mgba` is treated as GBA for artwork lookup).
- Filename, display title, cleaned dump/version tags, and the internal ROM header title are tried as bounded candidates.
- Successful covers are resized and saved as WebP under `persistent_data/Covers/<romId>/cover.webp`.
- A lightweight hero background is derived locally from the matched cover and cached under `persistent_data/Backgrounds/<romId>/background.webp`, avoiding a second network download.
- Lookup state is written to `persistent_data/Metadata/<romId>/artwork.json`.
- Failed exact matches are retried after seven days, or immediately through **Settings → Misc → Retry missing artwork**.
- User-selected covers/backgrounds are marked manual and are never overwritten by automatic artwork.

## Performance safeguards

- One low-priority native worker performs all artwork network/decode work.
- Library cards use `IntersectionObserver` so normal lookup is requested only near the viewport.
- Downloads are capped at 6 MiB and decoded images are bounded before caching.
- Covers are capped at 640×960 and backgrounds at 960×540.
- Artwork work defers while gameplay is active and resumes after the emulator closes.
- Existing cached images are reused; Retra does not redownload artwork every launch.
- A **Wi-Fi only** setting is available for automatic artwork.

## Accuracy rule

Automatic lookup intentionally prefers an exact title candidate and does not assign artwork from a merely similar/base game. If no confident exact result exists, Retra keeps the generated fallback color rather than showing the wrong box art. This is especially important for ROM hacks.

## Compatibility

The v4.28 Pokémon Flash 1M / 128 KB save-memory fix and v4.29 Library multi-select responsiveness changes are preserved. `app/src/main/cpp/native-lib.cpp` is unchanged from v4.29.
