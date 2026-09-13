# Retra v1.0.2 — Update Checking and Reinstall Recovery

## In-app update flow

Retra checks the official `lascent/Retra` GitHub Releases `latest` endpoint on a bounded background network executor. A successful automatic check is rate-limited to once every six hours. The About page also exposes a manual **Check for updates** action that always performs a fresh check.

If a newer semantic version is available, Retra shows an in-app update prompt. The **Update** action opens only an official HTTPS GitHub release or APK URL. Android/browser installation confirmation remains mandatory; Retra does not silently install packages.

## Reinstall continuity

Retra declares `android:hasFragileUserData="true"`. On Android versions/OEM builds that support the feature, uninstalling Retra can therefore show a **Keep app data** option. If the user keeps the data and later installs the same signed package again, Retra can reuse the retained local data.

Retra also remains opted into Android Auto Backup and device-to-device transfer. Its backup rules exclude managed ROM binaries (`library_content/`) and transient emulation save working files (`save_work/`) while keeping persistent user data such as saves/save states, Room library/statistics metadata, DataStore/legacy preferences, and the WebView-backed UI state eligible for system backup.

Android system backup is not a hard guarantee: users can disable backup, OEM behavior differs, cloud backup is quota-limited, and choosing not to keep data during uninstall can erase local private storage. Retra's `.retra` backup remains the explicit, user-controlled disaster-recovery path.

## Version

- `versionName = 1.0.2`
- `versionCode = 449`
