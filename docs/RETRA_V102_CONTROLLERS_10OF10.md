# Retra v1.0.2 — Controller Input 10/10 Hardening

This pass hardens Retra's Android on-screen gameplay controls against multi-touch and lifecycle edge cases.

## Fixed

- D-pad + A/B simultaneous presses can no longer corrupt the D-pad direction or inject LEFT.
- The D-pad has one touch owner and uses only that pointer's container-local coordinates.
- A/B, L/R, Start and Select use pointer ownership, thumb-drift retention and safe CANCEL/OUTSIDE cleanup.
- Independent input sources now use per-key hold counts, so releasing A cannot release an A that is still held by AB/LA/RA/Turbo AB.
- Combo controls use the same pointer-ownership and cancellation rules as the primary buttons.
- Turbo AB is generation-guarded so delayed pulses from an old pause/menu/close session cannot reassert keys.
- `releaseAllKeys()` invalidates in-flight touch generations, clears source counts, native tap latches and pressed visuals.
- D-pad touch handling was split into `GameplayTouchController.kt` to keep the high-rate input path focused and maintainable.

## Validation

- Controller-focused tests: passing.
- Full project regression suite: 359 / 359 passing.
- Architecture line-limit guard: passing.

A real-device gameplay pass is still recommended for final hardware/OEM validation because Android touch dispatch can vary by device firmware.
