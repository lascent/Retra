# Retra v4.29 — Multi-select latency + More popup fix

- Fixes the post-long-press delay that blocked taps on other ROM cards for ~650 ms.
- Click suppression is now scoped only to the ROM card that triggered the long press, so additional selections react immediately.
- Moves the multi-select More popup above the selected-count row with a higher z-index.
- Scales the Remove from Library popup down for a cleaner mobile fit.
- Uses a cheaper outline-based selected state to reduce Android WebView paint work.
- Preserves the v4.28 Pokémon ROM-hack Flash 1M / 128 KB compatibility fix.
