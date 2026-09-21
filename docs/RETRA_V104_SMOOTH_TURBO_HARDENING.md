# Retra v1.0.4 — SmoothTurbo hardening

## Goal

The v1.0.4 pass hardens the v1.0.3 latest-frame GPU architecture without changing its core rule: **emulation owns game speed; presentation owns display cadence**. The focus is to remove avoidable allocation and scheduling pressure from the frame path, make speed changes deterministic, and keep high turbo multipliers from creating a presentation backlog.

## Frame pipeline

```text
mGBA core
    |
    v
TurboSlicePlanner + TurboThroughputGovernor
    |
    v
Emulation worker
    |  newest completed frame
    v
GameplayFrameMailbox
    producer / pending / rendering / spare
    |  generation-aware latest-frame handoff
    v
GameplayFramePresenter / Choreographer VSync
    |
    v
ShaderGameView GL render thread
    |
    v
Android display
```

Audio remains independent:

```text
mGBA PCM -> native speed-aware FIR -> AudioController queue -> dedicated AudioTrack writer
```

## Four-buffer mailbox

`GameplayFrameMailbox` now preallocates four pixel arrays for each configured native video size:

1. **producer** — filled by JNI/mGBA;
2. **pending** — newest completed frame awaiting the GL consumer;
3. **rendering** — currently owned by the GL thread;
4. **spare** — immediate handoff headroom when producer and consumer cadence briefly diverge.

Older pending frames are recycled rather than queued. This guarantees that fast-forward does not produce a visual catch-up backlog.

The normal producer API accepts a reusable `PublishResult`, and the GL path reuses a `RenderFrame` holder. The hot publish path contains no framebuffer allocation. If the pool invariant is unexpectedly violated during a lifecycle/OEM race, Retra drops that visual publish and returns the completed buffer to the producer instead of allocating in the middle of a GPU stall.

## Fractional turbo pacing

The GBA base cadence is approximately 59.73 Hz, so a fixed rounded number of emulated frames per presentation interval can create a slow beat. `TurboSlicePlanner` distributes neighboring integer batch sizes so cumulative emulated time remains aligned with the selected multiplier.

`TurboThroughputGovernor` remains the wall-clock authority. A substantial scheduler/GC hitch re-anchors timing rather than trying to repay the lost time with a burst of back-to-back batches.

## VSync hardening

`GameplayFramePresenter` now:

- coalesces real mailbox generations with an atomic compare-and-set loop;
- reuses the `Runnable` objects posted to the target view;
- schedules the next Choreographer callback directly when already on the VSync/UI callback;
- resets pending/presented generation state when a gameplay session starts.

The session reset matters because mailbox generations restart when a ROM/video configuration is rebuilt. Without a presenter reset, the first generation of a new session could numerically match an old presented generation.

## Speed-transition hardening

When 1×/2×/4×/8×/16× changes, `EmulationSessionManager` resets:

- the normal frame pacer;
- the cumulative turbo governor;
- the fractional `TurboSlicePlanner`;
- `AudioController`'s active transform state.

The reset happens before the first emulated frame at the new speed, preventing a previous-rate timing/audio state from leaking into the new multiplier.

## Turbo audio

Fast-forward keeps the native `readAudioSamplesAtSpeed()` path backed by the existing 32-tap polyphase FIR resampler. At 2×/4×/8×/16×, only the wall-clock PCM Android can play crosses JNI instead of copying the full raw turbo stream into Kotlin. `AudioTrack` writes remain on the dedicated writer thread.

## Display policy

The v1.0.4 hardening keeps the stable 60/120 Hz gameplay presentation policy. Speed Mode does not force a physical panel-mode switch; emulation throughput remains independent. On high-refresh panels the system may still expose higher UI refresh rates elsewhere, but the gameplay presentation contract intentionally avoids turbo-time 144/165 Hz mode forcing.

## Regression guarantees

The v1.0.4 tests explicitly guard:

- four preallocated frame buffers;
- no `IntArray` allocation inside the hot mailbox publish path;
- reusable producer and GL result holders;
- stale-frame drop/recycle semantics;
- presenter generation reset per session;
- reusable VSync Runnables and CAS generation updates;
- pacing/planner/audio reset on speed changes;
- native turbo audio and latest-frame presentation ordering.
