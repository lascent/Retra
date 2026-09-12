# Retra v3.84 orientation layout persistence fix

This build fixes portrait/landscape controller drift and cross-orientation state leakage.

- Portrait and landscape use separate SharedPreferences keys.
- Screen frame state is also separate per orientation.
- Old v3.82 controller/screen layout state is intentionally not reused, preventing previously corrupted coordinates from reappearing.
- Native controls are positioned with FrameLayout margins instead of x/y translations, so Android relayouts during rotation cannot shift them.
- Rotation is debounced until the emulator viewport has its final dimensions.
- The active orientation comes from Android Configuration rather than a transient width/height measurement.
- Editor bridge writes are accepted only when payload orientation is explicitly `portrait` or `landscape`.
- Saving portrait only updates portrait; saving landscape only updates landscape.
- HTML Screen Editor localStorage keys were versioned to V384 so old mixed editor coordinates do not leak into the fixed layout.

After this one-time reset, each orientation persists until the user edits that same orientation in the Screen Editor.
