# Retra v1.0.2 — True 16x / My Boy!-style turbo

This pass separates **emulation throughput** from **visible presentation**.

## 16x behavior

- GBA CPU/game logic targets the full selected 16x multiplier (~955.6 core frames/s).
- 16x runs eight core frames per native JNI batch.
- mGBA turbo-only frameskip is 7 at 16x, so roughly one of each eight internal frames is rendered while all CPU/game logic frames still execute.
- 8x uses frameskip 3 for the same reason.
- Android presentation is sampled independently at a stable 60 Hz from the newest completed frame.
- The throughput governor uses cumulative deadlines. Scheduler oversleep creates temporary debt that later batches repay instead of permanently lowering the selected speed.
- If hardware cannot sustain the selected multiplier, Retra stops waiting and runs the core continuously at the device's maximum possible throughput.
- The mGBA audio ring is 8192 frames so an eight-frame native batch can be drained safely with fewer JNI/audio-pump crossings.

Normal 1x timing and the user's saved frameskip setting remain unchanged outside turbo.
