# Contributing to Retra

Thanks for improving Retra.

## Before opening a pull request

1. Keep ROM identity, saves and user data backward compatible.
2. Do not bundle commercial ROMs, BIOS files, artwork or copyrighted game assets.
3. Keep emulation timing independent from UI refresh-rate changes.
4. Preserve independent portrait and landscape controller layouts.
5. Add or update regression tests for behavior changes.

Run the local release gate:

```bash
python3 tools/release_gate.py
```

For Android work, also run:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

A full native APK build additionally requires the validated mGBA source checkout expected by `app/src/main/cpp/CMakeLists.txt`.

## Code organization

Prefer focused controllers/repositories over adding responsibilities back into `MainActivity`. Keep Web UI features in bounded modules and avoid per-frame preference/database/file I/O.

## Pull requests

Describe:
- the user-visible change;
- persistence/migration impact;
- performance impact;
- tests performed;
- devices/Android versions used for behavior that depends on hardware, Bluetooth, Wi-Fi, insets or refresh rate.

## Versioning

Retra public releases use Semantic Versioning:

- `1.0.x` — backward-compatible fixes and release hardening;
- `1.x.0` — backward-compatible feature releases;
- `2.0.0` — major changes that intentionally break compatibility or substantially redefine public behavior.

Do not reintroduce legacy internal build identifiers as public release versions. Pre-v1.0 milestones are maintained in `docs/DEVELOPMENT_HISTORY.md`.

## Architecture boundaries

Before adding behavior to `MainActivity`, check whether it belongs in an existing controller/repository or a new focused component. Keep `MainActivity` as lifecycle/composition glue, keep controller feedback out of pointer-input code, and keep shared domain models outside Activity nested classes.

Run `python3 tools/release_gate.py` before submitting changes; the architecture regression tests enforce these boundaries. See `docs/ARCHITECTURE.md`.
