# Retra v1.0.2 — 16x Frame Pacing 10/10 Pass

This pass keeps Retra's selected 16x emulation speed independent from Android display refresh while improving visible frame cadence.

## What changed

- Added `TurboBatchSequencer`, a fractional/Bresenham-style 16x batch scheduler.
- On fast panels, one 16x native batch is phase-aligned to one display interval when the device has enough throughput.
- 120 Hz example: the exact requirement is ~7.9637 GBA frames per display interval, so Retra uses mostly 8-frame batches with occasional 7-frame batches instead of forcing 8 every time.
- The cumulative `TurboThroughputGovernor` still owns game speed. Changing batch size does not change the requested 16x multiplier.
- The governor can rebase once to a real `Choreographer` VSync timestamp, bounding phase drift between emulation batches and presentation.
- Extreme turbo can use 144/165 Hz panel modes when available and thermally appropriate. Normal-speed GBA gameplay retains the cadence-compatible 60/120 Hz policy.
- If measured throughput falls behind, display-synchronized batching is disabled automatically and Retra returns to the lower-overhead 8-frame throughput path.
- Adaptive mGBA renderer skip now has a mild and severe level, preserving more source-frame headroom when possible.
- Sustained throughput recovery restores the user's normal renderer-skip setting.

## Speed guarantee model

The 16x clock remains based on cumulative completed emulated frames:

`target wall time = epoch + completedFrames * GBAFrameTime / 16`

The fractional display batcher only decides how those completed frames are grouped into JNI calls. It does not multiply, divide, or otherwise alter the 16x timing target.

## Validation

- 329/329 Node regression tests pass.
- Release validation passes.
- Release gate passes.
- Pure Kotlin turbo timing classes compile successfully.
- 60-second fractional-batch simulation at 120/144/165 Hz measures approximately 15.99989x; the tiny short-window difference is only the final fractional frame and converges to exactly 16x over time.

A full Android Gradle compile was not available in the offline validation environment because the configured Gradle 9.6 distribution was not already cached.
