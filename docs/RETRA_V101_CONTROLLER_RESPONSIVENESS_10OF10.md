# Retra v1.0.1 — Controller Responsiveness / 10-of-10 Target

This pass focuses on the native gameplay controller path, especially A/B. It preserves Retra's authentic ~59.73 FPS GBA timing; it does not speed the game up or fabricate frames.

## Implemented plan

1. **Immediate touch-to-core input**
   - A/B, L/R, Start and Select assert on `ACTION_DOWN` instead of waiting for click/up behavior.
   - Emulator input is updated before the pressed-state redraw.

2. **Robust multi-touch ownership**
   - Each single-button gesture owns one pointer ID.
   - A second finger cannot steal the first button's gesture.
   - The gameplay viewport and A/B group explicitly enable split motion events so D-pad + A/B simultaneous input stays independent.

3. **Thumb-drift tolerance without sticky buttons**
   - A held button gets a small Android touch-slop retention zone.
   - Small natural thumb movement does not release A/B accidentally.
   - Moving clearly outside releases immediately; re-entering the real button bounds can press again.

4. **Sub-frame tap capture**
   - Native input now latches rising key edges until mGBA samples them.
   - A very fast press+release that happens between two ~16.74 ms GBA frames is therefore still visible to the game for one frame instead of being missed completely.
   - The same rising-edge protection is applied to local-link/scheduled key masks.

5. **Lower hot-path overhead**
   - Android keeps a compact gameplay key bitmask and drops duplicate state transitions before JNI/Remote Link work.
   - `releaseAllKeys()` only emits releases for keys that are actually held instead of sending ten unnecessary releases.

6. **No stuck/ghost input across lifecycle changes**
   - Going to the background releases controller state before emulation/Remote Link is paused.
   - Menu/pause/close cancellation clears pending native tap latches.
   - Pressed visuals are recursively reset across the gameplay controller tree.

## Expected result

- Faster-feeling A/B response on rapid taps.
- Fewer missed ultra-short taps.
- More reliable D-pad + A/B multi-touch.
- Less accidental release from small thumb drift.
- No carried/stuck input after backgrounding, menu opening, or closing gameplay.
- No change to authentic GBA timing or game speed.

## Validation target

The source regression suite checks the low-latency binding, pointer ownership, multi-touch splitting, duplicate suppression, native sub-frame latch, and lifecycle cleanup. Final tactile latency still depends on the phone's digitizer, Android input stack, panel refresh rate, thermal state, and the individual game's own input logic, so real-device validation remains the final measurement step.
