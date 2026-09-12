# Retra v1.0 — Gameplay smoothness and image-quality pass

This pass targets the native mGBA gameplay path. It does **not** speed normal GBA emulation up to 120 FPS. The GBA remains at its real ~59.73 FPS cadence; 120 Hz-capable Android displays are used to reduce presentation latency and improve frame delivery without changing game speed.

## Implemented

- **Cadence-aware display selection** — gameplay prefers 60 Hz or 120 Hz-style integer multiples of the GBA source cadence. A 90 Hz mode is no longer preferred over a compatible 60 Hz mode for ~60 FPS content because 90 Hz produces uneven 1/2-refresh cadence.
- **Absolute-deadline frame pacing** — the mGBA thread now uses an absolute frame deadline with a tiny bounded precision window instead of relying only on a short scheduler sleep/park.
- **Android scheduling priority** — Automatic uses display-priority scheduling; Performance can use urgent-display priority; Compatibility stays at default priority.
- **Zero-copy Java frame publish** — the completed native framebuffer is published by swapping reusable front/back `IntArray` buffers instead of copying all GBA pixels a second time in Kotlin.
- **Non-blocking game audio output** — `AudioTrack` writes no longer block the mGBA frame loop. Partial writes are retained and retried, while a bounded backlog prevents stale Bluetooth/route audio from adding large control latency.
- **Lower audio headroom** — normal game audio targets about 50 ms of explicit headroom (subject to Android's device minimum) rather than roughly 100 ms.
- **Normal hardware compositor path** — Retra no longer forces the changing game `ImageView` or entire DecorView into cached hardware layers. Window hardware acceleration remains enabled.
- **Hidden WebView suspension** — the Retra HTML UI stays attached for instant return-to-Home behavior, but its renderer/timers are paused while native gameplay is visible.
- **JNI framebuffer pinning** — the output `IntArray` is pinned only for the short post-emulation pixel conversion step, avoiding a possible temporary JNI array copy each frame.
- **Crisper fresh-install scaling** — nearest-neighbor scaling is the fresh-install default for GBA pixel art; users can still enable Linear filtering.
- **Density-neutral framebuffer** — the 240×160 framebuffer is treated as raw pixels so Android density does not add an implicit scaling stage.

## Expected behavior

- Normal-speed gameplay remains approximately **59.73 FPS**, matching GBA timing.
- On a **60 Hz** display, frames map approximately 1:1.
- On a **120 Hz** display, each native GBA frame can be presented across roughly two refreshes, reducing presentation wait/input-to-display latency.
- Retra does **not** fabricate extra animation frames. True 120 unique game frames would require frame interpolation and can introduce artifacts/latency, so it is intentionally not used.

## Recommended user settings for smooth GBA gameplay

- Max frame skips: **0**
- Hardware rendering: **On**
- Linear filtering: **Off** for crisp pixels, On only if the user prefers smoothing
- CPU profile: **Automatic**; use Performance only on devices that cannot hold native speed
- Battery Saver: Off when maximum smoothness is desired

## Validation

The regression suite includes gameplay-smoothness checks for pacing, cadence-aware refresh selection, WebView suspension, frame-buffer swapping, non-blocking audio, compositor policy, crisp default scaling, and JNI framebuffer handling.

### 9. Single-owner frame skipping

Retra now lets **mGBA alone** apply the Frameskip setting. The Kotlin presentation loop no longer skips frames a second time. This avoids compounding a user-selected `Frameskip = 1` (already roughly 30 unique rendered FPS in mGBA) with another presentation-side skip.

For the smoothest gameplay, keep Frameskip at **0** unless a genuinely slow device needs it.
