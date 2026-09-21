# Retra v1.0.4 — Release Notes

Retra v1.0.4 focuses on high-speed gameplay smoothness and production hardening of the latest-frame GPU pipeline. The emulation core, video presentation, and audio output remain decoupled so Speed Mode can advance the game at the selected multiplier without building a renderer backlog.

## Highlights

- **Four-buffer latest-frame mailbox** — producer, pending, rendering, and spare buffers are preallocated for each native video size.
- **Allocation-free normal frame handoff** — the emulation producer and GL consumer reuse mutable result holders instead of creating new result objects every frame.
- **No emergency framebuffer allocation in the hot path** — if an unexpected pool invariant is violated, Retra drops that visual publish and keeps emulation progressing rather than allocating during an existing GPU stall.
- **Smooth fractional turbo slices** — `TurboSlicePlanner` distributes neighboring integer batch sizes to match the GBA's ~59.73 Hz base cadence while preserving long-term 2×/4×/8×/16× timing.
- **Hitch-safe turbo recovery** — substantial Android/GC stalls re-anchor the cumulative governor rather than producing a visible catch-up burst.
- **Safer VSync scheduling** — real mailbox generations are coalesced with CAS, posted Runnables are reused, and the presenter resets its generation state for every new gameplay session.
- **Cleaner speed transitions** — changing the multiplier resets pacing, turbo planning, and the audio transform before the first frame at the new speed.
- **Native turbo audio** — the existing 32-tap polyphase FIR path keeps high-speed PCM compression native so unnecessary raw 8×/16× audio does not cross JNI.

## Presentation policy

Retra continues to keep game speed independent from screen presentation. The gameplay surface uses a stable cadence-compatible 60 or 120 Hz path for the session rather than forcing 144/165 Hz mode changes during Speed Mode. Faster emulated states may be intentionally skipped visually; the newest completed state is presented and stale pending states are recycled.

## Compatibility

The v1.0.4 changes do not alter save formats, ROM identity, save-state storage, controller-layout storage, or backup formats. Existing v1.0.3 installations can upgrade normally through Android using `versionCode 451`.

## Validation

The dependency-light release gate passes **423/423 regression tests** and protects the four-buffer pool, allocation-free hot publish/acquire paths, session generation reset, reusable VSync scheduling, speed-transition resets, exact cumulative turbo behavior, latest-frame stale dropping, native turbo audio path, UI/XML integrity, and release metadata consistency.

Physical-device validation is still recommended across low-, mid-, and high-end Android hardware, especially sustained 8×/16× sessions, audio transitions, lifecycle pause/resume, and thermal load.
