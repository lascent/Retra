# Retra v1.0.1 — Fully adaptive Android UI hardening

This pass upgrades Retra's edge-to-edge WebView shell from phone-oriented safe-area handling to a window-adaptive Android layout.

## What changed

- Android now enters edge-to-edge before the first content layout, reducing first-frame geometry jumps.
- The native WindowInsets bridge remains the single source of truth for system bars and display cutouts.
- Native Android runtime is marked explicitly; only that runtime expands the app shell to the real window. Desktop/browser preview keeps the 428×926 preview shell.
- The Android app no longer caps normal landscape windows to a 428 px portrait mock-phone.
- Left/right safe insets now protect content from landscape cutouts and side system UI.
- Headerless pages consume the actual top inset instead of relying on a fixed top gap.
- Centered dialogs are bounded by the real safe area and remain scrollable in short landscape/split-screen windows.
- Tablet/foldable spacing scales from current window width with CSS breakpoints, never device model or resolution checks.
- The Activity is explicitly resizable and handles `smallestScreenSize` / `screenLayout` changes without forcing an emulator-session recreation.
- `adjustResize` is enabled for IME behavior; persistent navigation safe-area geometry remains independent from keyboard height to avoid double bottom padding.
- WindowInsets are refreshed on configuration changes such as rotation, fold/unfold and split-screen resizing.

## Design rule

Retra uses **actual app-window geometry + native WindowInsets**, not device detection, resolution tables, or fixed navigation-bar guesses. System insets are applied only by the component that owns that physical edge.
