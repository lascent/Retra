# Retra v4.08 — Functional Settings Pass

This build wires the previously placeholder/dead emulator settings into the Android/native layer.

## Misc
- Google Drive save sync through Android's Storage Access Framework (choose a folder in the Google Drive provider).
- Drive sync options: Sync now, Change folder, Disconnect.
- Import battery saves and save states from a selected folder.
- Open app folder now uses a user-selected persistent export folder and exports Retra save data there.
- Auto Save & Load creates/restores an automatic state per ROM.
- ROM patching automatically matches IPS/UPS/BPS patches by ROM basename.
- Enable cheats controls whether stored cheats are applied on ROM launch.
- Confirm on close/reset, Full screen, and Immersive mode are now connected to gameplay.
- Fast-forward button mode now supports both Press to toggle and Hold down to activate.

## Video / Audio
- Stretch-to-fit, frame skip, hardware/software Android compositing, and linear filtering are wired to gameplay.
- Sound enable, master volume, and sample frequency are passed into mGBA runtime config, and gameplay audio is streamed to Android AudioTrack.

## Advanced
- CPU Core choices are now meaningful runtime profiles: Automatic, Performance, Compatibility.
- Use BIOS / Boot BIOS now pass through to mGBA.
- BIOS file picker accepts GBA, GB, and GBC BIOS files and stores them in Retra private storage.
- Speed optimization maps to mGBA idle-loop optimization.
- Reset Advanced Settings now restores the native-backed advanced values.

## Persistence
Settings are stored in Android SharedPreferences and are restored on app restart. Cloud save sync runs on startup, save-state changes, auto-save, and after core shutdown so battery saves can be uploaded after they flush.
