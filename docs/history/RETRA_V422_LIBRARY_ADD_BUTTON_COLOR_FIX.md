# Retra v4.22 — Library Add Button Color Fix

- Fixed the Library **+ Add ROM** floating button losing its accent-colored fill after being tapped on Android/WebView.
- The button now keeps the same theme/accent fill in normal, pressed, and touch-focus states.
- Preserved the subtle press-scale animation.
- Clears stale WebView focus after tapping so the button does not remain visually stuck after the ROM picker opens/closes.
