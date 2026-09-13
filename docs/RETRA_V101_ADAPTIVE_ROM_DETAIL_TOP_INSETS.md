# Retra v1.0.1 — Adaptive ROM detail top inset fix

- Fixes excessive empty space above ROM/game detail content after the edge-to-edge WindowInsets update.
- The ROM hero is now the single owner of the Android status-bar/display-cutout top inset.
- The parent `main` container contributes no additional top safe-area padding while the ROM detail page is open.
- Keeps only a compact responsive design gap (8–12 CSS px) after the real system inset.
- No device model, resolution, density, orientation, or navigation-mode heuristics are used.
