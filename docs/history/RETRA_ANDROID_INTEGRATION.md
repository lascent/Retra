# Retra Android Integration — UI v3.81

This project combines the Retra v3.81 HTML/CSS/JS frontend with the native Android mGBA integration.

## Native gameplay layout

The real gameplay screen follows the v3.81 defaults:

- **Screen Editor only:** Back at top-left and Add Controller (+) at top-right. These do not appear during gameplay.
- **Landscape gameplay:** emulator screen centered, correct aspect ratio, most of the vertical height; Menu top-center; L/R top corners; D-pad lower-left; A/B lower-right; Start/Select bottom-center; all controller groups at 100% size.
- **Portrait gameplay:** emulator screen at the top and nearly full width while preserving aspect ratio; L/R directly below it; Menu centered between them; Start/Select above A/B in the lower-right-middle; D-pad bottom-left; A/B bottom-right; all controller groups at 100% size.

The Menu button opens native Quick Load, Quick Save, Fast Forward, and Exit Game actions so those utility buttons do not need to remain visible on the gameplay overlay.

## Supported content

Retra accepts `.gba`, `.gbc`, `.gb`, `.mgba`, `.zip`, `.ips`, `.ups`, and `.bps` through the native Android importer. ZIP bundles can contain supported ROMs and patches. Standalone patches ask for a compatible clean/base ROM.

## Folder layout

Keep the Android project and mGBA source side-by-side:

```
C:\Users\corti\AndroidStudioProjects\
├── Retra\
└── mgba\
```

Then open `Retra` in Android Studio, sync Gradle, clean/build, and run on the device.
