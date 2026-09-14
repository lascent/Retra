# Retra v1.0.2 — Turbo Video + Audio 10/10 pass

This pass keeps the existing emulation-speed targets unchanged while improving how 8x/16x work is delivered to the display and audio device.

## Video
- Generalizes fractional display-synchronized batching from 16x to both 8x and 16x.
- 8x can now produce fresh states at 144/165 Hz when the device exposes those modes instead of being limited by a fixed four-frame batch cadence near 119.455 batches/s.
- 16x keeps the fractional VSync-aligned scheduler on sufficiently fast panels.
- Completed frames are handed to the VSync presenter immediately after the native batch, before audio resampling or monitoring work, reducing missed display deadlines.
- The cumulative throughput governor remains authoritative, so smoother presentation does not change the selected game-speed multiplier.

## Audio
- Replaces the old resample-then-average turbo path with a single native speed-aware polyphase FIR conversion.
- Uses a 32-tap Blackman-windowed low-pass sinc so time compression and Android sample-rate conversion happen in one anti-aliased pass.
- Keeps 8x/16x audible by default while generating only the PCM Android actually plays.
- Enables Android low-latency AudioTrack mode, lowers the normal prebuffer/track targets, and retains adaptive underrun recovery.
- Coalesces tiny extreme-turbo PCM packets to reduce queue/allocation churn.
- If a ROM/device measurably cannot sustain 8x/16x, audio is temporarily muted and drained natively before the game-speed target is sacrificed; hysteresis restores audio after recovery.

## Validation
- All source regression tests pass.
- `AudioController.kt`, `TurboFramePolicy.kt`, and `TurboBatchSequencer.kt` pass standalone Kotlin compilation with Android API stubs.
- 60-second batch-sequencer simulation converges to ~7.999944x at 8x and ~15.999888x at 16x on supported synchronized display rates.
- Full Gradle compilation still requires the Gradle 9.6 distribution, which is not available in the offline validation environment.
