# Retra v1.0.2 — My Boy-style fast-forward throughput fix

This patch changes the real execution path, not only the speed labels.

- 8x now batches 4 core frames per JNI call; 16x batches 8.
- 8x/16x bypass Retra's expensive high-quality audio resampler and drain generated PCM inside the same native turbo batch (no extra JNI hop).
- mGBA video/audio sync are explicitly disabled because Retra owns pacing.
- User frameskip is preserved initially; extra renderer skipping is enabled only if measured throughput remains below target.
- Display presentation remains independent and VSync-driven, always showing the newest completed frame.

Target core rates remain based on native GBA timing (~59.7275 Hz): 2x ~119.46, 4x ~238.91, 8x ~477.82, 16x ~955.64 emulated frames/s. Actual achievable speed still depends on device/ROM workload, but the old avoidable JNI/audio bottlenecks are removed.
