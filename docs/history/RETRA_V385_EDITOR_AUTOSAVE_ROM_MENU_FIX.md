# Retra v3.85 fix

- Portrait and landscape controller layouts use separate persistence keys and never cross-save during rotation.
- Native gameplay orientation is chosen from the measured emulator viewport, preventing stale-orientation top-control drift.
- Leaving Settings > Video > Screen size auto-commits the active orientation's controller and emulator-screen layout to Android.
- Actual gameplay restores the saved controller positions, scales, hidden state, and emulator screen state for the current orientation.
- ROM detail three-dot popup no longer overlaps the ROM title; the title is kept below the popup while open.
- ROM detail vertical spacing was tightened.
