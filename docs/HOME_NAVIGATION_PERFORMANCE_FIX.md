# Retra Home Navigation Performance Fix

This release keeps the Home/Library UI warm instead of treating every tab return like a fresh screen.

## Changes

- Main tabs switch immediately without the opacity-from-zero page animation that could expose a blank/black WebView frame.
- Home, History, and More preserve their scroll position; Home also preserves its active category and search state.
- The ROM grid is signature-cached. If library data did not change, Retra keeps the existing card DOM and decoded cover images instead of destroying/recreating them.
- Cover images use lazy loading and asynchronous decoding.
- Automatic cover lookup work is scheduled during idle/background UI time.
- Re-tapping the active main tab is a no-op and does not trigger storage or rendering work.
- The Android WebView remains attached/layout-ready while native gameplay is visible (`INVISIBLE` rather than `GONE`).
- When returning from native gameplay, Retra reveals the already-warm WebView before removing the emulator overlay on the next frame, preventing a black transition frame.

## Storage behavior

Tab navigation does not query Room/SQLite, rescan ROM files, or decode covers again. Library rendering is refreshed by existing change events (import/remove/favourite/category/media changes), while unchanged navigation uses the in-memory/local cache.

## Validation

`tests/home-navigation-performance.test.cjs` verifies the navigation state cache, render signature cache, main-tab animation removal, and warm-WebView transition contract.
