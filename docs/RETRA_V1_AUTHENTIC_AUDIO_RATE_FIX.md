# Retra v1.0.0 — Authentic Game Audio Rate Fix

## Problem

Retra was reading PCM directly from `mCore::getAudioBuffer()` and sending those samples to Android as if they were already generated at the selected 44.1/48 kHz output rate. That assumption is incorrect for mGBA. The core exposes its actual emulated audio rate through `mCore::audioSampleRate()`, and GBA software can use hardware rates such as 32768 Hz or 65536 Hz.

Playing 32768/65536 Hz PCM through a 44100 Hz `AudioTrack` without resampling changes the perceived pitch, tempo, tone and timing. Short button/click sounds can still seem acceptable, while continuous background music makes the mismatch much easier to hear.

## Fix

- Query the active mGBA core's real audio rate every audio drain.
- Use mGBA's own `mAudioResampler` with `mINTERPOLATOR_SINC` for high-quality band-limited conversion.
- Resample into the exact Retra Android output rate before Kotlin queues PCM to `AudioTrack`.
- Preserve resampler continuity when the emulated hardware rate changes.
- Reset interpolation state when switching ROM cores or local-link players so unrelated PCM is never blended together.
- Keep the existing dedicated Android audio thread, prebuffer, underrun recovery, A/B alignment, and speed-mode behavior.

## Expected result

At 1x speed, game background music should now have the correct original tempo and pitch, with cleaner timbre and stable timing. Fast-forward/slow-motion still intentionally time-scales game audio together with gameplay.
