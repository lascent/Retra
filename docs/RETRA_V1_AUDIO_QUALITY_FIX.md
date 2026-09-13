# Retra v1.0.0 — Audio Quality / Static Fix

This pass hardens the Android PCM output path for cleaner music and sound effects while keeping emulation timing independent from Android audio scheduling.

## Changes

- Fixes the main pitch/tone bug in the previous Android path: Retra no longer assumes mGBA's raw core PCM is already 44.1/48 kHz. GBA core audio can run at hardware rates such as 32768/65536 Hz and can change at runtime.
- Routes raw mGBA PCM through mGBA's own high-quality **windowed-sinc audio resampler** before it reaches Android, converting the real core rate to the exact `AudioTrack` output rate. This keeps normal-speed music tempo, pitch, tone and timing faithful instead of playing raw hardware-rate samples at the wrong clock.
- Detects runtime core sample-rate changes and updates the resampler continuously without changing the Android playback clock.
- Moves `AudioTrack` writes off the emulation frame thread onto a dedicated `THREAD_PRIORITY_AUDIO` writer.
- Uses blocking writes only on that audio worker, so partial PCM blocks are completed without stalling gameplay.
- Adds a short 32 ms startup/recovery pre-buffer and an 80 ms device buffer target to reduce underruns caused by normal Android scheduler jitter.
- Detects `AudioTrack` underruns and pause/flush/re-primes instead of resuming into an already-starved stream, which avoids common click/static bursts.
- Flushes stale queued PCM when changing normal/turbo/slow-motion rates and fades the new stream in over about 5 ms.
- Keeps turbo anti-alias averaging and slow-motion stereo interpolation so speed modes stay synchronized and cleaner than sample dropping/repetition.
- Bounds queued audio to 200 ms and drops stale backlog after route/device stalls instead of letting sound become delayed.

## Validation

- Full source regression suite: 178 tests passing.
- `AudioController.kt` syntax/type smoke-compiled against Android API stubs.
- Runtime smoke exercised normal speed, 2x turbo, 0.5x, 0.2x, pause/resume, and release lifecycle.

The source-level audio-rate mismatch is corrected and regression-tested. A final physical-device listen test is still required for the signed release APK, especially on the phone speaker, wired/USB audio, and Bluetooth if those routes are supported.
