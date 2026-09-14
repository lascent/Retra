# Retra v1.0.2 — 16× speed + smoothness pass

This pass keeps Retra's true 16× wall-clock target while removing the main sources of visible extreme-turbo hitching.

## Changes

- 16× native bursts are capped at four emulated frames (~4.2 ms target wall time) instead of eight (~8.4 ms). This returns control to Android frequently enough for UI/RenderThread to meet VSync while retaining batched JNI execution.
- 8×/16× presentation now follows the selected gameplay panel cadence up to 120 Hz instead of being forced to 60 Hz. Low-RAM, battery-saver and thermally constrained devices still fall back through `DisplayPerformanceManager`.
- At 8×/16× `GameplayFramePresenter` keeps a direct Choreographer VSync callback alive. Emulator frame publication only increments an atomic generation; it no longer needs a View.post hop for every visible frame. Lower speeds remain event-driven.
- Extreme turbo uses `THREAD_PRIORITY_DISPLAY`, not `URGENT_DISPLAY`, so the emulator does not compete unfairly with Android's compositor/RenderThread.
- mGBA frameskip remains 3 at 8× and 7 at 16×. This is renderer-only skipping: game logic still advances at the selected multiplier, while roughly 120 distinct rendered states per second are available for high-refresh presentation.

## Expected behavior

On a device that can sustain the workload, 16× should retain the same game-speed target while looking materially smoother on 120 Hz displays. On constrained/thermal devices Retra favors stable presentation and lets the throughput governor run the core flat-out rather than introducing artificial sleeps.

Real-device validation is still required for a literal 10/10 rating because CPU/GPU scheduling, screen refresh modes, thermal throttling and screen recording overhead differ by phone.
