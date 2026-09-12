# Retra v3.82 dual-layout fix

This project is based on the supplied `Retra(1).zip` and keeps the existing mGBA integration.

## Fixed
- Portrait and landscape gameplay now use different default geometry.
- Portrait: game screen at the top, L/Menu/R in the lower-middle row, Start/Select above A/B, D-pad bottom-left, A/B bottom-right.
- Landscape: game screen centered and tall at the correct aspect ratio, Menu top-center, L/R top corners, D-pad lower-left, A/B lower-right, Start/Select bottom-center.
- Back and `+` remain Screen Editor-only and stay hidden during gameplay.
- Portrait and landscape controller edits are saved independently.
- Portrait and landscape emulator-screen resize/mode settings are saved independently.
- Screen Editor now follows the actual editor orientation instead of hard-coding landscape.
- New preference/storage keys reset stale v3.81 layout data so the corrected defaults appear cleanly.

## Project hygiene
Generated Android Studio/Gradle/CMake build caches were removed from the distributed ZIP. Android Studio will recreate them on Sync/Build.

Keep the project next to the mGBA source folder:

```
C:\Users\corti\AndroidStudioProjects\
├── Retra\
└── mgba\
```
