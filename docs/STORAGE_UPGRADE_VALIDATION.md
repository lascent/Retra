# Persistent Storage Upgrade Validation

## Passed in this source package

- `node --test tests/*.test.cjs`: 68/68 passing in v4.41.
- `node --check` across every `app/src/main/assets/retra/*.js` module: passing.
- `python3 tools/validate_release.py`: passing.
- Release validator confirms unique HTML IDs, valid Android XML and no exposed release placeholders.
- Source-contract tests cover Room ownership, DataStore ownership, import-journal states, per-ROM filesystem roots, custom media, final patched-ROM identity aliases and non-destructive Library removal.
- Android instrumentation source includes a legacy SQLite v1 fixture that verifies migration into the Room v2 store without changing `romId` or losing existing path/metadata fields.

## Requires Android Studio / device validation

A full Gradle/NDK build was not executable in the packaging environment because the Gradle distribution/dependencies are not cached locally and external download access is unavailable. The mGBA source checkout is also intentionally external to this ZIP.

Before publishing an APK, run:

```text
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

with a compatible mGBA checkout, then test on physical Android hardware:

1. Upgrade an existing v4.24 installation and confirm Room migration.
2. Import, rename/move, remove and re-import the same ROM.
3. Force-stop during import at multiple points and verify journal recovery.
4. Confirm battery saves and save states survive Library removal/re-import.
5. Confirm portrait/landscape controller + screen layouts are independent for two ROMs.
6. Confirm covers/backgrounds survive restart/re-import.
7. Confirm patched ROMs reconnect through final-ROM SHA-256 and legacy identity aliases.
8. Test Local Link and Remote Link for regressions.
