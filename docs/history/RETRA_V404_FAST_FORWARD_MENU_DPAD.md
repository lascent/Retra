# Retra v4.04 — Silent Fast Forward, Tap-Outside Menu Close, Smooth D-pad

## Fast Forward
- Removed the gameplay Toast/notification when toggling Fast Forward.
- Selecting **Fast forward** from the in-game menu now applies the configured speed immediately and closes the menu so gameplay resumes at that speed.
- Toggling Fast Forward back off is also silent.

## In-game Menu
- The gameplay dialog is now cancelable by touching outside its menu frame.
- Tapping the dimmed game area closes the menu and resumes the emulator.
- Android Back behavior for menu/submenus remains unchanged.

## D-pad
- Replaced four isolated direction touch handlers with continuous whole-D-pad tracking.
- The D-pad now follows the thumb during `ACTION_MOVE`, so users can slide/roll between Up, Down, Left, Right and diagonals without lifting their finger.
- Added a small center dead-zone to prevent accidental direction flicker.
- Added forgiving diagonal detection for smoother movement on touchscreens.
- Duplicate JNI key updates are suppressed; only real direction changes are sent to the emulator core.
- Pressed-state visuals now follow the currently active direction(s).
