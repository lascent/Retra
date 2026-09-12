# Retra v3.94 — Added Controls Gameplay Sync + Landscape 100%

## Fixed
- Quick Load, Quick Save, and Fast Forward controls added in Settings > Video > Screen size now appear during real gameplay.
- Their saved editor position and scale are applied in the same emulatorViewport coordinate system used by the Screen Size editor.
- Removing one of these controls from an orientation hides it from gameplay for that orientation.
- Portrait and landscape continue to use independent saved controller datasets.

## Landscape scale
- Landscape default controller scale is now 100% (portrait remains 100%).
- A one-time migration converts legacy 125% saved landscape controls from v3.90-v3.93 to 100% while preserving other custom scales.

## Notes
- Screen Editor-only Back and Add Controller controls remain hidden during gameplay.
- Controller Opacity also applies to Quick Load, Quick Save, and Fast Forward when visible.
