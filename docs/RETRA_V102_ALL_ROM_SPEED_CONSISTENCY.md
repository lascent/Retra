# Retra v1.0.2 — All-ROM Speed Consistency

Retra now applies one fast-forward timing policy to every supported ROM and ROM hack.

- 2x, 4x, 8x and 16x all use the same cumulative wall-clock governor.
- Scheduler oversleep is repaid by later batches instead of permanently lowering game speed.
- Measured throughput adaptation is global rather than limited to 8x/16x.
- If a ROM is heavier, Retra first doubles native batch efficiency (up to 16 frames), then reduces renderer work, and only mutes turbo audio under a severe sustained miss.
- The selected multiplier is never silently changed to compensate for a slow ROM.
- 8x/16x retain high-refresh fractional VSync presentation when the device has enough headroom.

This cannot create CPU performance that the device does not have. If a ROM cannot physically reach a requested multiplier even with all optional work removed, Retra runs mGBA flat-out rather than lying about the clock.
