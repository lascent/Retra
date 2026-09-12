# Retra v3.86 remaining fixes

- ROM detail bottom spacing reduced to a small safe-area-aware gap.
- Change BG / Change Cover menu forced above the ROM title with a high stacking layer and no slide/tween.
- Removed the Screen Editor emulator inner/selection frame; outer shell remains.
- Controller drag bounds now reach the true screen edges; resize UI no longer reserves a bottom strip.
- Portrait and landscape editor state swaps are atomic and fully independent.
- Rotation no longer lets resize observers clamp/rewrite the old orientation against the new viewport.
- Old inline controller geometry is cleared before measuring the other orientation, then that orientation's own saved state is restored.
- Rendering a saved custom emulator frame is read-only; it is changed only by an explicit editor resize.
