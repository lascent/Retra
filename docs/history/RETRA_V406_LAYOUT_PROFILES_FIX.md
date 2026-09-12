# Retra v4.06 — Working Layout Profiles + FAB Positioning

## Layout profiles
- The `+` button in Settings > Layouts now opens a **New layout profile** name dialog.
- Pressing **OK** creates/selects the profile and immediately opens the real Screen Size / Screen Editor.
- The pencil button edits the currently selected profile.
- Each profile stores its own **Portrait** and **Landscape** controller + emulator-screen layouts.
- Selecting a profile applies its saved data to actual native gameplay, not only the preview.
- Screen Editor Back continues to auto-save and now also updates the active layout profile.
- New profiles begin as a copy of the currently active layout so the user can adjust from a familiar baseline.
- Profiles persist across app restarts.

## Positioning polish
- Categories `+ Add` FAB moved slightly farther upward.
- Layouts `+` FAB moved farther upward and slightly left from the screen edge.
- Both FABs respect Android safe-area / gesture insets.
