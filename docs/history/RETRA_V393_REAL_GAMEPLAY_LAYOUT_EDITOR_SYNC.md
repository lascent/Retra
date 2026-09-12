# Retra v3.93 — Real Gameplay Layout Editor Sync

## Screen Size / Layout Editor
- The Screen Size editor now edits the same saved layout used by native gameplay.
- Back auto-saves the current orientation; no Save button is needed.
- Controller + emulator-screen state is committed atomically before leaving the editor.
- Native SharedPreferences are the gameplay source of truth and are reloaded into the editor.
- Portrait and landscape remain completely independent datasets.
- Rotation swaps datasets without converting or rewriting the other orientation.

## Exact editor/game coordinate canvas
- Entering the Screen Size editor uses the same full-screen/system-bar-hidden canvas as gameplay.
- The editor phone shell no longer adds its own inset coordinate space.
- Editor controller base dimensions now match the native Android controls (Menu, L/R, D-pad, Start/Select, A/B).
- Normalized x/y positions and scale therefore map consistently between editor and gameplay.

## Emulator screen matching
- Portrait Best mode now uses the same top position in editor and gameplay.
- Landscape Best mode now uses the same 90% height bound in editor and gameplay.
- Break Aspect Ratio / Stretch now uses FIT_XY in portrait as well as landscape.
- Custom frame left/top/width/height remains orientation-specific and persistent.

## Persistence
The customized layout remains after:
- Back from Screen Editor
- starting/reopening a ROM
- leaving and returning to gameplay
- app restart
- device rotation
