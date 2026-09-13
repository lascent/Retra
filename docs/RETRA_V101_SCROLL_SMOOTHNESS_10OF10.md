# Retra v1.0.1 — Scroll Smoothness 10/10 Pass

This pass keeps Retra scrolling native to Chromium/Android WebView. It does not add JavaScript scroll interpolation or artificial easing.

## Changes

- Adds a passive scroll coordinator that only tracks active scrolling and defers non-urgent background UI work until scrolling settles.
- Gives vertical and horizontal scroll surfaces explicit pan ownership so the browser can enter compositor scrolling immediately.
- Removes the persistent Android bottom-navigation backdrop blur, avoiding an expensive re-blur of moving content behind the navigation bar.
- Extends `content-visibility`/containment to History, Statistics playtime rows, and ROM-detail save rows.
- Starts Home cover-card virtualization at 36 ROMs instead of waiting for 80 ROMs.
- Ensures statistics cover artwork uses lazy loading, asynchronous decoding, and low fetch priority.
- Disables row/card transition work while an actual scroll is in progress.
- Preserves native momentum, direct finger tracking, edge-to-edge safe insets, existing controller behavior, and all gameplay timing.

## Design rule

Retra deliberately keeps `scroll-behavior: auto` on the main application surfaces. Smoothness comes from reducing rendering/main-thread work per frame, not from delaying the user's gesture with scripted smoothing.


## Tap-only Settings/More card guard

Retra's navigation cards now distinguish a true tap from a hold or scroll drag.
A normal quick tap still opens on release. Holding for 420 ms or moving beyond
the drag threshold cancels card activation, and any WebView-synthesized release
click is consumed before the page-navigation handler runs. This prevents a
settings card from opening after the user was only holding it or beginning a
scroll gesture.

On Android touch devices, per-row Settings/More backdrop blur is also disabled;
the existing card backgrounds remain, while native WebView scrolling avoids
repainting stacked blur layers during 60/90/120 Hz movement.
