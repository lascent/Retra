# Retra v1.0.2 — Extreme Turbo Wall-Clock Presentation

At 8x/16x the emulation core and visible rendering are intentionally decoupled.
Visible frame capture is scheduled from `System.nanoTime()` rather than nominal
emulation progress. If the device cannot sustain an exact 16x core rate, Retra
drops invisible intermediate frames and continues presenting the newest completed
frame at an even real-time cadence. Missed presentation deadlines are re-anchored
instead of being caught up with back-to-back renders.

The native turbo loop also avoids redundant per-hidden-frame MOSAIC renderer
resynchronization; the existing register hook and explicit setting update already
keep that policy correct.
