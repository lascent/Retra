# Retra v4.43 — Adaptive 60–120 Hz gameplay presentation

Retra keeps emulator timing independent from display refresh. GBA remains ~59.73 FPS, while completed frames are presented on the next Android VSync.

## Adaptive policy

- Low-RAM / under 4 GiB / battery saver / severe thermal state: cap at 60 Hz.
- 4–6 GiB or moderate thermal state: cap at 90 Hz.
- Capable devices: up to 120 Hz when the panel exposes a matching mode.

The presenter is event-driven: it requests one Choreographer callback only when a new emulator frame has completed, so a 120 Hz panel does not create a continuous 120 Hz CPU polling loop.

The build also moves remaining legacy playtime-key access behind StatisticsRepository to fix the post-v4.41 unresolved-reference compile regression.
