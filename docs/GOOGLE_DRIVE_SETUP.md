# Retra v1.0.3 — Google Drive API setup

Retra's cloud backup path uses Google Identity Services authorization plus Google Drive API v3. It does **not** use Android's document-tree picker for Google Drive.

Cloud snapshots are stored in:

`My Drive / Retra Backups /`

The app requests only:

`https://www.googleapis.com/auth/drive.file`

No OAuth client secret is embedded in the Android app.

## Google Cloud configuration

1. Open the Google Cloud project used by Retra and enable **Google Drive API**.
2. Configure **Google Auth Platform / OAuth consent**:
   - App name: Retra
   - Add the support/developer contact email.
   - Choose the appropriate audience (External for ordinary public Google accounts).
   - Add the Drive scope `https://www.googleapis.com/auth/drive.file` under Data Access.
   - While the OAuth app is in Testing, add every Google account that will test Retra as a test user.
3. Create an **OAuth 2.0 Client ID → Android**:
   - Package name: `com.retra.emulator`
   - SHA-1: the certificate fingerprint of the APK/AAB actually installed on the device.
4. Register every signing certificate that will be used:
   - Android Studio/debug builds: debug keystore SHA-1.
   - Direct/sideload release builds: release signing-keystore SHA-1.
   - Google Play builds with Play App Signing: create/use an Android OAuth client for the **App signing certificate SHA-1** shown in Play Console → App integrity.
5. SHA-256 may also be registered where Google/Firebase/Play configuration asks for it, but it does not replace the Android OAuth client's required package-name + SHA-1 association.
6. Build/install Retra with Google Play services available on the device. On first cloud use, select the Google account and grant the requested Drive permission.

## Verification checklist

After authorization:

1. In Retra, enable **Automatic Google Drive backup** or tap **Backup Now**.
2. Wait for the success status and last-backup timestamp.
3. Open the Google Drive app or drive.google.com with the same account.
4. Confirm `My Drive/Retra Backups` exists and contains a `.retra` snapshot.
5. Make another local change and run Backup Now. The same `Retra Backups` folder should be reused; a new validated snapshot may be added, but the folder itself must not be duplicated.
6. Reinstall/clear local Retra only after confirming a good cloud snapshot exists. Connect the same account, choose **Restore from Google Drive**, select a snapshot, and verify saves, save states, settings, controller layouts, custom ROM names/covers, statistics, and playtime return.

## Troubleshooting OAuth

If authorization fails on-device, first compare the installed APK's signing SHA-1 with the Android OAuth client in Google Cloud. A debug APK, a directly signed release APK, and a Play-App-Signing APK can all have different SHA-1 fingerprints.

If the OAuth consent configuration is still in Testing, verify the selected account is listed as a test user. For production distribution, complete the Google Auth Platform publishing/verification steps appropriate to the requested scopes.
