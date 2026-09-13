# Retra v1.0.1 — Interaction & Motion Polish

This pass improves perceived responsiveness inside Retra without changing emulator timing or controller geometry.

## Changes

- Added a single motion-token layer for press, fast, standard and sheet transitions.
- Added pointer-down feedback before click handlers execute, with drag cancellation so scrolling remains natural.
- Replaced the old page fade-from-zero with destination-first compositor motion to avoid blank/dark flashes.
- Added distinct fast tab motion and subtle forward/back subpage motion.
- Restricted bottom-sheet transitions to `transform` and `opacity` instead of broad property animation.
- Restricted switch animation to `background-color` and thumb `transform`.
- Added lightweight modal/search entry motion.
- Disabled redundant card-level backdrop blur on coarse-pointer Android devices while preserving the visual backing layers.
- Preserved `prefers-reduced-motion` behavior.
- Kept Screen Editor/gameplay control geometry outside the generic press system.

## Design target

The UI should react on finger-down, settle quickly on release, and present the destination before decorative motion begins. The intent is a lighter, more native-feeling Android interaction model rather than longer or more noticeable animations.
