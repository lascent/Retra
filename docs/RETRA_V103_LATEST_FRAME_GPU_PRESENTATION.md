# Retra v1.0.3 — Latest-frame GPU presentation

> **Historical note:** This document describes the v1.0.3 presentation foundation. Retra v1.0.4 keeps this design and hardens it with a four-buffer allocation-free hot path, session-safe VSync generation handling, and deterministic turbo audio transitions. See [`RETRA_V104_SMOOTH_TURBO_HARDENING.md`](RETRA_V104_SMOOTH_TURBO_HARDENING.md).

This pass changes how Retra presents gameplay during normal play and Speed Mode. The emulator core and the Android renderer are now independent producer/consumer stages rather than one frame-delivery chain.

## Why this exists

Fast-forward can advance hundreds of emulated GBA states per second, while an Android display can show only 60–120 useful updates per second. Queueing or presenting every completed state causes visible catch-up bursts, uneven frame delivery, and the "dragging" feeling that can occur even when the selected 2×/4×/8×/16× speed is mathematically correct.

The new rule is simple: **emulation owns game speed; presentation owns display cadence**.

## Pipeline

```text
mGBA emulation worker
        |
        |  exact 1× / 2× / 4× / 8× / 16× timing
        v
GameplayFrameMailbox
  producer / pending / rendering
        |
        |  newest frame only; stale pending video is recycled
        v
Choreographer VSync sampler
        |
        v
GLSurfaceView render thread
        |
        v
OpenGL ES texture / optional shader
        |
        v
Android Surface at stable 60 or 120 Hz
```

## Triple-buffer latest-frame mailbox

`GameplayFrameMailbox` owns three steady-state frame roles:

- **producer** — the buffer JNI/mGBA is currently filling;
- **pending** — the newest completed frame not yet consumed by the renderer;
- **rendering** — the frame currently owned by the GL thread.

If a newer frame arrives before the renderer consumes `pending`, the older pending frame is returned to the free pool. It is never queued for later presentation. This prevents fast-forward from building a visual backlog.

A small emergency buffer allocation is permitted only if an OEM GL thread stalls long enough to exhaust the normal pool. Released render buffers are recycled into the pool on the next acquire.

## GPU presentation for normal gameplay

The pass-through OpenGL renderer is now the normal gameplay path, even when the user has no GLSL shader selected. The legacy `ImageView` remains only as an OEM compatibility fallback.

This moves framebuffer conversion/upload work off Android's UI/Choreographer thread. The UI thread only requests a draw at VSync; `ShaderGameView` acquires and uploads the latest frame on its dedicated GLSurfaceView renderer thread.

Custom GLSL shaders continue to use the same surface and therefore do not switch presentation architectures.

## Speed Mode cadence

Speed Mode no longer changes the physical display mode when the user presses the speed button. Retra chooses one cadence-compatible gameplay mode at session start and keeps it stable.

- panels that expose a clean high-refresh cadence use **120 Hz**;
- constrained or 60 Hz paths use **60 Hz**;
- 144/165 Hz turbo forcing is not used by this runtime path;
- the Android 11+ surface frame-rate API receives the same stable 60/120 Hz hint.

Turbo batch size is derived from the selected multiplier and that presentation cadence:

| Speed | 120 Hz path | 60 Hz path |
|---|---:|---:|
| 2× | ~1 frame/slice | ~2 frames/slice |
| 4× | ~2 | ~4 |
| 8× | ~4 | ~8 |
| 16× | ~8 | ~16 |

Only the final state of each native slice crosses JNI as pixels. Every hidden emulated CPU/game frame still runs.

## Accurate speed

`TurboThroughputGovernor` remains the cumulative wall-clock authority for every fast-forward multiplier. Presentation FPS does not determine game speed. If hardware cannot sustain a requested multiplier, the governor stops waiting and mGBA runs flat-out; the renderer still presents frames in order without a catch-up queue.

## Frameskip and audio

Speed Mode no longer raises mGBA renderer frameskip automatically. The user's Frameskip setting remains authoritative, avoiding the case where the core advances correctly while the visible framebuffer repeats an older state.

Fast-forward audio remains enabled and is processed on Retra's dedicated audio writer path. Video publication occurs before audio/performance bookkeeping so a completed game frame is not held back by those systems.

## Scheduling

The emulator worker uses a favorable background/display-adjacent priority during turbo, but it does not claim Android's urgent display priority. Android RenderThread and the GLSurfaceView consumer retain scheduling headroom to present the frame that mGBA already finished.

## Compatibility

The legacy `ImageView`/Bitmap presenter is retained as a fallback if a device cannot create/use the OpenGL gameplay surface. Save-state thumbnails and one-off shader-selection snapshots intentionally use copies because those operations are outside the frame hot path.

## Validation

The release regression suite protects:

- exact cumulative turbo timing;
- 60/120 Hz stable presentation;
- latest-frame stale-drop semantics;
- pass-through GL as the normal gameplay path;
- no automatic turbo frameskip escalation;
- audible fast-forward audio;
- video publication before audio/performance bookkeeping.

## Smooth Turbo Upgrade

The latest-frame GPU path now keeps the original triple-buffer mailbox while tightening the remaining fast-forward hot paths:

- **Fractional native slices:** `TurboSlicePlanner` distributes neighboring integer batch sizes instead of permanently rounding the ideal 60/120 Hz batch size. This removes the slow cadence beat caused by the GBA's ~59.73 Hz base timing while preserving the selected 2x/4x/8x/16x emulated-time target.
- **Continuous turbo VSync sampling:** Speed Mode keeps a Choreographer callback armed at the stable gameplay presentation cadence. A GL draw is still requested only when the mailbox generation changes, so presentation remains latest-frame-only without creating a stale-frame queue.
- **Generation-aware handoff:** mailbox publication now returns the completed frame generation, and the presenter coalesces requests by that real generation instead of maintaining an unrelated request counter.
- **Hitch-safe cumulative governor:** normal turbo timing remains cumulative, but a substantial Android/GC stall re-anchors the epoch instead of paying the delay back as a burst of catch-up batches.
- **Native turbo audio compression:** fast-forward audio uses the existing native 32-tap polyphase FIR through `readAudioSamplesAtSpeed`. Only wall-clock PCM crosses JNI at 2x/4x/8x/16x; the dedicated AudioTrack writer thread remains unchanged.

The user-selected mGBA frameskip value remains authoritative. Automatic core frameskip is intentionally not re-enabled here because forcing renderer skip without guaranteeing that the final turbo slice frame was rendered can expose an older framebuffer. The upgrade instead reduces pacing, JNI, and presentation overhead around the existing latest-frame design.
