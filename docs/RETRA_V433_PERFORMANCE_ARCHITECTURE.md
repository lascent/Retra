# Retra v4.33 — Performance Architecture & High-Refresh UI

Retra v4.33 focuses on responsiveness and maintainability without changing emulator timing, ROM identity, saves, layouts, or existing library behavior.

## Adaptive 60 / 90 / 120 Hz UI

`DisplayPerformanceManager` now owns Android display refresh selection. It:

- keeps hardware acceleration enabled;
- selects the highest same-resolution display mode up to 120 Hz;
- reapplies the request after resume and orientation/configuration changes;
- restores the Activity's original display preference during teardown.

This affects Android/WebView UI rendering only. The mGBA emulation loop remains at `16,742,706 ns` per frame (~59.73 FPS), so a 120 Hz display improves UI smoothness without doubling game speed.

## WebView responsiveness

`WebUiPerformanceTuner` centralizes WebView performance policy:

- explicit hardware rendering;
- off-screen pre-raster support for the warm UI shell;
- important renderer priority while native gameplay temporarily covers the WebView;
- unnecessary WebView scrollbars/overscroll disabled.

The existing warm-WebView navigation path is preserved to avoid black/blank frames when returning from gameplay.

## Organized background work

`RetraTaskExecutors` now owns Activity-scoped background pools:

- `Retra-Storage-*` is intentionally single-threaded so migration/import journal ordering stays deterministic;
- `Retra-Link-*` handles concurrent network/link work without blocking storage or the UI;
- shutdown is centralized in one owner.

## Large-library paint optimization

For libraries with 80+ ROMs, the WebView marks the ROM grid as `large-library`. Card paint/layout containment and `content-visibility` allow Chromium to skip unnecessary off-screen card raster work while preserving card geometry.

On coarse-pointer/mobile devices, per-card favourite badge blur is disabled during normal use to reduce GPU composition cost while keeping the translucent visual appearance.

## Compatibility rules preserved

- Permanent `romId` and SHA-256 reconnect logic are unchanged.
- Save/state paths and crash-safe import journal are unchanged.
- GBA timing remains ~59.73 FPS.
- 60/90/120 Hz refers to the app UI/display refresh request, not accelerated emulation.
## v1.0.0 release hardening

The v1.0.0 release pass removes additional work from latency-sensitive paths:

- frame-skip state is cached in the Activity runtime instead of reading SharedPreferences inside every emulation frame;
- Remote Link background work uses a bounded 2–4 worker pool with a finite queue and centralized shutdown;
- Screen Editor pointer streams are coalesced to at most one drag/resize update per display paint using `requestAnimationFrame`;
- the editor uses `ResizeObserver` to keep selection handles aligned after preview, inset, orientation, or foldable-size changes;
- coarse-pointer devices receive larger virtual resize hit areas without visually enlarging controller artwork.

These changes target responsiveness and scheduling stability. They do not alter mGBA timing or ROM/save identity semantics.

