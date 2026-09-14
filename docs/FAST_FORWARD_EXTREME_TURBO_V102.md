# Retra v1.0.2 — Extreme Turbo Rendering Hardening

This pass targets the remaining visible jank at 8× and 16× fast-forward without changing normal-speed emulation timing.

## Runtime plan

1. **Short turbo slices stay bounded**
   - Turbo work remains capped to four emulated frames per slice.
   - The slice is paced against the requested emulation speed instead of running one large 8/16-frame burst.

2. **One JNI boundary per turbo slice**
   - `runTurboSlice()` advances all frames in the slice natively.
   - Held and edge-latched input is sampled on every emulated frame.
   - Hidden frames never perform Java framebuffer conversion.
   - Only the final frame is converted when presentation is due.

3. **Audio is drained once per safe slice**
   - mGBA's native ring is configured for 4096 stereo frames.
   - A turbo slice is capped at four core frames, so Retra can drain/resample once per slice instead of once per hidden frame.
   - The existing speed-transform accumulator and dedicated AudioTrack writer remain unchanged.

4. **Rendering gets scheduler headroom**
   - At 8×/16× the emulation worker no longer runs continuously at display-thread priority.
   - Android's UI, RenderThread, GLSurfaceView thread and audio writer can preempt it when needed.
   - 4×+ also disables the tiny normal-speed busy-spin precision window.

5. **120 Hz extreme-turbo QoS**
   - 8×/16× on a 120 Hz panel publish at stable 60 Hz, which is an exact 2:1 display cadence.
   - 60/90 Hz panels retain their native useful presentation cadence.
   - The emulator core still runs as fast as the device can sustain; only redundant framebuffer conversion/upload work is reduced.

## Expected effect

- Fewer JNI transitions at 8×/16×.
- Fewer expensive pixel conversions and GPU uploads at extreme turbo on 120 Hz phones.
- Less UI/GL starvation from the high-priority emulation worker.
- Lower CPU contention and more consistent frame presentation.
- Better input responsiveness while fast-forward is active.

Actual 16× emulation speed remains hardware/game dependent. A shader-heavy game or a demanding ROM can still be limited by the phone's CPU/GPU, but Retra now spends substantially less work on frames that cannot be displayed.
