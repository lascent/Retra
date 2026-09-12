# Retra v4.36 — Android navigation-safe Settings scrolling

## Problem

On some Android phones, especially with classic 3-button navigation, the last
row of a Settings detail page could sit underneath the system navigation bar.
This made rows such as **Open app folder** and **Reset advanced settings** hard
or impossible to reach, even after scrolling to the bottom.

## Fix

- Added `WebUiInsetsManager` to read the real Android navigation-bar/display-cutout
  bottom inset from `WindowInsetsCompat`.
- Converts the native physical-pixel inset to WebView/CSS pixels and forwards it
  to the packaged UI through `window.retraSetNativeBottomInset(...)`.
- The existing WebView viewport/safe-area heuristic remains as a fallback; Retra
  now uses the larger of the native inset and web fallback.
- All subpages reserve a scroll tail using `--device-safe-bottom`, so their last
  control can be moved completely above gesture or 3-button navigation.
- Insets are refreshed when the warm WebView becomes visible again after gameplay.

## Compatibility

This does not alter emulator timing, ROM identity, save data, controller layouts,
or the 60/90/120 Hz display policy.
