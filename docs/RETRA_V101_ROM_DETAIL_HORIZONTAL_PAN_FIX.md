# Retra v1.0.1 ROM Detail Horizontal Pan Fix

## Problem

The Android adaptive layout removes the normal `main` side gutters while a legacy ROM-detail rule still applied negative left/right margins. Those two rules together made the ROM detail page wider than the device viewport, allowing the whole screen to be dragged horizontally.

## Fix

- Reset ROM-detail left/right margins to `0` in the native Android runtime.
- Keep the ROM detail width capped at `100%` of the current Android viewport.
- Disable horizontal overflow/overscroll on the ROM-detail scroll container while preserving normal vertical scrolling.
- Preserve the existing full-bleed hero, safe-area insets, recent-save edge treatment, and adaptive layout.

## Regression protection

`tests/rom-detail-spacing.test.cjs` now verifies that Android ROM details remain viewport-locked and cannot regain horizontal panning.
