# Retra v1.0.0 — Authentic HQ audio conditioning

This pass targets the rough/harsh background-music character heard in the supplied Harvest Moon recording without changing the mGBA mixer, game timing, pitch, stereo image, or music tempo.

## Output path

`mGBA mixer -> Retra band-limited resampler -> 18 Hz DC blocker -> gentle mGBA-style single-pole smoothing -> 0.99 headroom -> PCM16 -> AudioTrack`

## Why

- The existing resampler already prevents aliasing and saturates PCM16 safely.
- The supplied capture still exhibited a noticeable DC component and harsh high-frequency energy.
- mGBA's libretro frontend includes an optional single-pole low-pass specifically to reduce generated-audio harshness. Retra adopts the same topology at a gentler 22% smoothing strength rather than the much stronger 60% libretro default.
- A ~18 Hz DC blocker removes sub-audible offset while leaving musical bass effectively unchanged.
- 1% headroom protects the final Android PCM16 handoff from inter-stage peaks without audible loudness loss.

The filter is stateful across audio pulls and is reset whenever the core/audio source is reset, preventing stale filter history from leaking between games or link players.
