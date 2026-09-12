# Retra v3.97 — Categories + History Fix

## Categories
- Moved the Categories `+ Add` button slightly upward for better spacing.

## History
- History now records ROMs only after Android confirms that the ROM successfully started.
- History is stored persistently and survives app restarts.
- Replaying a ROM updates it to the top of History and increments its play count instead of creating duplicate rows.
- History rows show ROM title, system, last-played time, play count, and cached cover when available.
- Tapping a history row opens its ROM details.
- The Resume action in History launches that ROM again.
- Search History now filters real saved history entries.
- Clear History now clears both the UI and saved history data.
- Incognito mode is now functional and pauses new History recording while enabled.
