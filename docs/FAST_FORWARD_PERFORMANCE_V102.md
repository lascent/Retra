# Retra v1.0.2 Fast-Forward Performance Plan

## Goal
Keep 2x/4x/8x/16x fast-forward responsive and visually smooth without changing normal 1x emulation accuracy.

## Implemented pipeline

1. **Short turbo execution slices**
   - Fast-forward no longer runs a whole speed-sized batch and then sleeps.
   - Work is spread across roughly 4 ms slices.
   - 2x/4x use one core frame per slice, 8x uses two, and 16x uses four.

2. **No-video intermediate frames**
   - Intermediate turbo frames still execute the mGBA core, controller input, and audio.
   - They skip the expensive native-to-Java ARGB pixel conversion when that frame cannot be displayed.

3. **Display-aware publication**
   - Android video snapshots are capped to the useful selected display cadence (60-120 Hz).
   - On a 120 Hz device, 2x can expose nearly 120 unique game frames per second; higher turbo modes publish the newest useful frame while the core continues advancing faster internally.

4. **Audio stays per emulated frame**
   - PCM is drained after every core frame so the native ring does not overflow.
   - Output is packetized at presentation points to keep the audio writer off the emulation thread.

5. **Normal speed is unchanged**
   - 1x still targets the GBA source cadence (~59.73 FPS).
   - No frame interpolation or fake game frames are introduced.

## Expected result
The change primarily reduces bursty CPU/JNI work at high turbo ratios. At 16x, Android pixel conversion falls from potentially every ~956 emulated frames/second to only the panel-useful 60/90/120 snapshots/second while mGBA still advances at the requested speed when the device is capable.

## Validation
- Source/contract regression suite passes.
- JavaScript syntax and XML/release validation pass.
- Final Android/Kotlin/C++ compilation and real-device 2x/4x/8x/16x profiling should be run in Android Studio/GitHub Actions because this environment cannot download the Gradle distribution.
