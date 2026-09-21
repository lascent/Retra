# Retra Troubleshooting

This guide covers common problems with **Retra v1.0.4** and the first things to try before opening an issue.

**Current release:** [Retra v1.0.4](https://github.com/lascent/Retra/releases/tag/v1.0.4)

> Retra does not include ROMs, BIOS files, or copyrighted game assets. Use only files you are legally permitted to use.

## Before you start

1. Confirm that you are running **Retra v1.0.4** or a newer stable release.
2. Fully close Retra and open it again.
3. Restart the Android device if the problem continues.
4. Test another known-good ROM when possible to separate a game-specific problem from an app-wide problem.
5. Before changing storage or reinstalling the app, create a backup from **More → Data and Storage → Create backup**.

A `.retra` backup can contain saves, save states, cheats, library metadata, controller layouts, artwork, statistics, and portable settings. ROM and BIOS files are not included.

---

## Retra will not install or update

### Android says the APK cannot be installed

Retra v1.0.4 requires **Android 8.0 / API 26 or newer**.

Make sure the APK finished downloading and matches your device. If Android reports a package or signature conflict, the installed build may have been signed with a different key.

If you need to uninstall a conflicting build, create a `.retra` backup first so important saves and settings are protected.

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

The installed APK and the new APK were signed with different signing keys. Android will not install one over the other.

Create a backup, uninstall the conflicting build, install Retra v1.0.4, and restore the backup.

### `INSTALL_FAILED_NO_MATCHING_ABIS`

The APK does not contain native libraries compatible with the device CPU. Use the correct Retra APK for the device or a universal build when one is provided.

---

## A ROM does not appear in the Library

Try importing the ROM again with Android's file picker.

If only one game is affected:

- Confirm that the ROM file is not corrupted.
- Test an unmodified dump when possible.
- If an IPS, UPS, or BPS patch is involved, temporarily test without the patch.
- Avoid renaming a patch so that it incorrectly matches another ROM.

Retra uses ROM content identity for persistent game data, so moving or renaming the same compatible ROM should not normally remove its saves or metadata.

---

## A game will not start, shows a black screen, or freezes

Start with the normal configuration:

1. Open **Settings → Advanced**.
2. Set **CPU profile** to **Automatic**.
3. Leave **Cartridge save type** on **Automatic** unless a specific game requires an override.
4. Keep **Speed optimization** enabled.
5. If a BIOS is enabled, temporarily turn **Use BIOS** off and test again.
6. Open **Settings → Video → GLSL shader** and select **None**.
7. Restart the game.

If only one ROM is affected, test another clean copy or an unmodified revision before changing global Retra settings.

---

## Gameplay feels slow, stuttery, or uneven

For a clean performance test:

- Set **Settings → Video → Frameskip** to **0**.
- Keep **Hardware rendering** enabled.
- Set **GLSL shader** to **None** while troubleshooting.
- Keep **Settings → Advanced → CPU profile** on **Automatic**.
- Keep **Speed optimization** enabled.
- Return emulation speed to **1×** first to check normal-speed performance.
- Close demanding background apps and disable aggressive battery restrictions for Retra if Android is throttling it.

Retra v1.0.4 keeps emulation timing separate from display presentation. Gameplay uses a cadence-compatible **60/120 Hz** presentation path where supported, while the emulator continues to advance at the requested game speed.

### Fast-forward is not smooth at 2×, 4×, 8×, or 16×

v1.0.4 uses SmoothTurbo fractional scheduling and a four-buffer latest-frame pipeline. At high speed Retra intentionally presents the newest completed frame instead of queueing every emulated frame.

If a high multiplier still feels uneven:

1. Test **2×**, then **4×**, **8×**, and **16×** separately.
2. Keep Frameskip at `0` while diagnosing the issue.
3. Disable GLSL shaders temporarily.
4. Test after the device has cooled down; sustained 8×/16× can expose thermal or CPU limits.
5. If 1×/2× are smooth but only 8×/16× struggle, include the device model and exact speed in the bug report.

---

## Audio sounds distorted, crackly, too fast, or too slow

First return emulation to **1×**. Fast-forward and slow-motion intentionally change game timing and therefore change how audio is presented while those modes are active.

Then try:

1. Open **Settings → Audio**.
2. Confirm **Enable sound** is on.
3. Set **Sound frequency** to **44100 Hz**.
4. Fully close and reopen the game.
5. Test another ROM to determine whether the problem is game-specific.

Retra v1.0.4 resets the turbo audio transform when the speed multiplier changes. If an audio problem only occurs during a specific multiplier, report the exact transition, for example `1× → 16× → 1×`.

---

## On-screen controls are misplaced, too large, or hard to press

Retra stores independent portrait and landscape layouts.

Open **Settings → Video → Screen size** to enter the Screen Editor, then rotate the device to edit the orientation you want.

You can:

- Drag controls to reposition them.
- Select a control and use the scale handle to resize it.
- Move or resize the game screen.
- Add supported controls with the **+** button.
- Use **Reset layout** if a layout becomes unusable.

Layout profiles are available under **Settings → Layouts**.

---

## Saves or save states are missing

Do not immediately overwrite the game with a new save.

1. Confirm that you imported the same game/ROM content used previously.
2. Check whether the ROM was patched or replaced with a different revision.
3. Open **More → Data and Storage → Retra storage** and verify that Retra data is still present.
4. To import a battery save manually, open the game → **Menu → Import save** and choose a matching `.sav` or `.srm` file. Retra validates the filename and save size before replacing the current save.
5. If you created a `.retra` backup, use **More → Data and Storage → Restore backup**.

Removing a game from the Library is intended to be non-destructive. Permanent game-data deletion is a separate action.

---

## Create Backup or Restore Backup is not working

Portable `.retra` backup and restore is supported in Retra v1.0.4.

### Create a backup

Go to **More → Data and Storage → Create backup**, choose the data to include, and save the `.retra` file with Android's file picker.

If saving fails:

- Choose a normal user-accessible location such as Downloads or Documents.
- Make sure the device has free storage space.
- Try another storage provider or folder in Android's picker.

### Restore a backup

Go to **More → Data and Storage → Restore backup** and select a valid `.retra` file.

`.retra` backups do **not** contain ROM or BIOS files. Re-import those separately when moving to another device.

---

## Google Drive backup or restore is not working

Google Drive controls are under **More → Data and Storage → Google Drive**.

Try the following:

1. Enable **Automatic Google Drive backup** if you want background protection.
2. Connect the intended Google account when prompted.
3. Use **Backup Now** to test an immediate upload.
4. Use **Restore from Google Drive** to recover a stored Retra backup.
5. Confirm that the device has internet access and that Google authorization was not cancelled.

Retra stores Drive backups in **My Drive/Retra Backups**. It does not upload ROM or BIOS files.

If cloud recovery is important, keep a local `.retra` backup as an additional copy.

---

## Cheats do not work or remain enabled unexpectedly

1. Open **Settings → Misc** and make sure **Enable cheats** is enabled.
2. Open the in-game menu and select **Cheats**.
3. Confirm that the code matches the correct game, region, and revision.
4. Disable the cheat and reopen the game if the state does not immediately return to normal.

Some cheats modify values that a game may later store in its own save. Turning a cheat off cannot always reverse changes that were already saved.

Cheats are unavailable during Local Link sessions.

---

## BIOS problems

Retra can run normal emulation without a user-supplied BIOS.

If you choose to use one:

1. Open **Settings → Advanced**.
2. Enable **Use BIOS**.
3. Select the correct BIOS file for GBA, GB, or GBC.

If a game stops working after BIOS support is enabled, disable **Use BIOS** and test again. Retra does not provide BIOS files.

A **16 KiB user-provided GBA BIOS** is required for the cartridge-less receiver used by Local Single-Pak/Multiboot.

---

## Artwork is missing or incorrect

Automatic artwork lookup can require an internet connection.

- Check the network connection.
- If **Artwork on Wi-Fi only** is enabled under **Settings → Misc**, connect to Wi-Fi or disable that restriction.
- Retry missing artwork after correcting the ROM title or metadata.
- Manual cover/background overrides should take priority over automatic artwork.

---

## Local Link, Single-Pak, Wi-Fi Remote Link, or Bluetooth Remote Link is not working

- Confirm that both players use compatible game versions.
- Return emulation speed to **1×** before starting a Link session.
- Keep Retra in the foreground and keep participating devices awake.

### Local Link / Multi-Pak

Use compatible GBA games that support normal Link Cable multiplayer.

### Local Single-Pak / Multiboot

Retra v1.0.4 supports **two-player Local Single-Pak/Multiboot on one Android device**. The cartridge-less receiver requires a user-provided **16 KiB GBA BIOS**. Game compatibility still depends on the title's own Single-Pak implementation.

### Wi-Fi Remote Link

Keep both devices on a stable local network. Avoid VPNs, client isolation, or restrictive hotspots while testing.

### Bluetooth Remote Link

Make sure Bluetooth is enabled and the required Android Bluetooth permissions are granted on both devices.

**Remote Link remains Multi-Pak-only. GBA Wireless Adapter / RFU emulation is not supported.**

---

## Update checking does not find v1.0.4

Retra checks the official GitHub Releases feed when a fresh app process starts and also provides **About → Check for updates**.

If an update is not detected:

1. Confirm that the device is online.
2. Fully close Retra and reopen it.
3. Use **About → Check for updates**.
4. Open the [Retra v1.0.4 release page](https://github.com/lascent/Retra/releases/tag/v1.0.4) directly to verify the release is published.

---

## Retra crashes or closes unexpectedly

Try to reproduce the crash once with a simple configuration:

- CPU profile: **Automatic**
- Frameskip: **0**
- GLSL shader: **None**
- Emulation speed: **1×**
- BIOS: disabled unless required for the test

If it still happens, open a report in [Retra GitHub Issues](https://github.com/lascent/Retra/issues).

Include:

- Retra version, for example `v1.0.4`
- Android version
- Device model
- Portrait, landscape, or both
- Game system: GB, GBC, or GBA
- Whether the ROM is patched or modified
- Exact reproduction steps
- Relevant settings
- Fast-forward multiplier if applicable
- Screenshots or a short screen recording when useful
- The exact Android error message, if one appears

Do **not** upload copyrighted ROMs, BIOS files, private save data, account credentials, signing keys, or other secrets to a public issue.

---

## Still having a problem?

Search [existing Retra issues](https://github.com/lascent/Retra/issues) before creating a new report.

Use a short descriptive title such as:

```text
Audio crackling at 16× after returning to 1× on Android 15
```

rather than:

```text
Retra broken
```

Clear reproduction steps, device information, and the exact Retra version make bugs much easier to diagnose.
