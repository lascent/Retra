# Retra v4.46 — Gameplay layout compile hotfix

Fixes the two unresolved references introduced while extracting `GameplayLayoutController` from `MainActivity`:

- imports `MainActivity.Companion.FAST_FORWARD_BUTTON_MODE_PREF`
- imports `android.content.res.Configuration`

No gameplay behavior or layout persistence logic is changed.
