# Retra v1.0.0 — Authentic Audio Device-Clock Pass

This pass builds on the real mGBA-rate sinc-resampling fix. It is intentionally aimed at authenticity and stability rather than adding EQ, bass boost, stereo widening, normalization, or other effects that would change the original soundtrack.

## Improvements

- Keeps mGBA's real `audioSampleRate()` as the source clock and the existing `mINTERPOLATOR_SINC` conversion path.
- Separates the emulator's 44.1 kHz high-quality source preference from the physical Android output clock.
- When the high-quality 44.1 kHz option is selected, Retra asks Android for the device-native music output rate (normally 48 kHz on modern phones) and builds `AudioTrack` at that native rate when it is valid.
- Reports the actual `AudioTrack` rate back to native code through `retra.outputSampleRate`, so mGBA's sinc resampler converts directly to the physical Android rate. This avoids an avoidable second 44.1 -> 48 kHz AudioFlinger resample on common devices.
- Preserves the low-frequency 22.05/11.025 kHz user choices instead of silently overriding them.
- Keeps the baseline 32 ms startup prebuffer and 80 ms active track buffer for normal devices.
- If Android reports a real underrun, Retra grows the prebuffer and active track buffer in small bounded steps (up to 64 ms / 112 ms), then gradually returns toward the normal targets after 30 seconds of stable playback.
- Keeps pause/flush/re-prime and the short fade-in after real starvation so recovery does not produce a sharp click or a burst of stale music.
- Adds no enhancement DSP. Original game pitch, tempo, balance, and timbre remain the goal.

## Why this helps background music

Continuous music exposes sample-clock conversion and underrun problems much more clearly than short UI-like sound effects. Directly resampling from mGBA's real source clock to the phone's physical output clock reduces conversion stages, while adaptive buffering prevents occasional device scheduler stalls from turning into crackle, gaps, or timing discontinuities.

## Validation

The source regression suite includes dedicated checks for native-rate output selection, native/Kotlin rate handoff, adaptive buffering, and preservation of the sinc/PCM path. A final signed-APK A/B listen test against desktop mGBA is still the final authority for a specific phone/audio route.
