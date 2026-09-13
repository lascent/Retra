# Retra v1.0.1 — Adaptive Android Bottom Insets

Retra now has one native safe-area owner for the packaged WebView UI. `WebUiInsetsManager` reads `systemBars()` and `displayCutout()` through AndroidX `WindowInsetsCompat`, converts the physical insets to CSS pixels, and forwards top/right/bottom/left values to the web layer.

## Layout contract

- Normal Retra pages run edge-to-edge.
- System bars remain visible outside gameplay unless the Screen Editor/gameplay presentation intentionally hides them.
- `--device-safe-bottom` is the only system-navigation clearance used by bottom-aligned Web UI.
- CSS `env(safe-area-inset-*)` remains only as a browser/PWA fallback in the central root variables.
- No phone model, resolution, navigation-mode, or viewport-height heuristic is used.
- The ROM category sheet keeps its scroll body and Close/Save footer separate; the footer alone consumes the bottom safe inset.
- Settings detail pages apply the bottom system inset at the outer subpage scroller only.

## Conceptual device matrix

| Case | Expected behavior |
|---|---|
| Small phone + gestures | Compact bottom gap matching only the gesture/navigation inset |
| Small phone + 3-button navigation | Controls remain above the system buttons with no duplicated clearance |
| Tall phone + gestures | No viewport-derived artificial blank strip |
| Tall phone + 3-button navigation | Real navigation inset is consumed once |
| Tablet | Same native inset contract; Retra component sizes remain unchanged |
| Portrait | Insets update from the current system-bar geometry |
| Landscape | Top/right/bottom/left safe areas update for bars and cutouts |
| Rotation / navigation-mode changes | `requestApplyInsets` refreshes the web safe-area values |

IME/keyboard insets are intentionally excluded from the persistent navigation-safe-area value so opening a text field does not resize the app's bottom navigation as though the keyboard were a system navigation bar.
