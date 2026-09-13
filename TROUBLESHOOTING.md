# Retra Troubleshooting

This guide covers common problems with Retra and the first things to try before reporting a bug.

> Retra does not include ROMs, BIOS files, or copyrighted game assets. Use only files you are legally permitted to use.

## Before you start

Before changing advanced settings:

1. Make sure you are using the latest stable Retra release.
2. Fully close Retra and open it again.
3. Restart your Android device if the issue continues.
4. Test another known-good ROM when possible. This helps determine whether the problem is specific to one game or to Retra itself.
5. Create a Retra backup before making major storage changes: **More → Data storage → Create backup**.

Retra `.retra` backups can include saves, save states, cheats, library metadata, controller layouts, artwork, and app settings. ROM and BIOS files are not included.

---

## Retra will not install or update

### APK says the app cannot be installed

Make sure the APK is complete and compatible with your device. Retra v1.0.1 requires Android 8.0 or newer.

If Android reports a package/signature conflict while updating, the installed build may have been signed with a different key. Back up your Retra data first, uninstall the conflicting build, and then install the official build you want to use.

Do not uninstall Retra before creating a backup if you have saves or settings you need to keep.

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

The installed APK and the new APK were signed with different signing keys. Android will not install one over the other.

Create a `.retra` backup, uninstall the conflicting Retra build, install the new build, and restore your backup.

### `INSTALL_FAILED_NO_MATCHING_ABIS`

The APK does not contain native libraries compatible with the device's CPU architecture. Use the correct Retra APK for your device or a universal build when one is provided.

---

## A ROM does not appear in the Library

Try importing the ROM again from Android's file picker.

If only one game is affected:

- Confirm that the ROM file is not corrupted.
- Test an unmodified dump when possible.
- If you use an IPS, UPS, or BPS patch, temporarily test without the patch.
- Avoid renaming a patch so that it incorrectly matches a different ROM.

Retra identifies persistent game data using ROM content identity, so renaming or moving a compatible ROM should not normally erase its Retra data.

---

## A game will not start, shows a black screen, or freezes

Start with Retra's normal/default configuration:

1. Go to **Settings → Advanced**.
2. Set **CPU profile** to **Automatic**.
3. Leave **Cartridge save type** on **Automatic** unless the game specifically requires an override.
4. Keep **Speed optimization** enabled.
5. If you enabled a BIOS, temporarily turn **Use BIOS** off and test again.
6. Go to **Settings → Video → GLSL shader** and select **None**.
7. Restart the game.

If the issue affects only one ROM, test another copy or an unmodified version of that game before changing global Retra settings.

---

## Gameplay feels slow, stuttery, or less smooth than expected

For the smoothest default setup:

- Go to **Settings → Video** and set **Frameskip** to **0**.
- Keep **Hardware rendering** enabled.
- Set **GLSL shader** to **None** while troubleshooting.
- Go to **Settings → Advanced** and keep **CPU profile** on **Automatic**.
- Keep **Speed optimization** enabled.
- Return gameplay speed to normal **1×** when diagnosing performance.
- Close demanding apps running in the background and disable Android battery-saving restrictions for Retra if your device is heavily throttling it.

A 90 Hz or 120 Hz phone can make Retra's interface and presentation feel smoother, but the emulated game's original timing is still controlled separately from the phone's display refresh rate.

---

## Audio sounds distorted, crackly, too fast, too slow, or different from normal

First return emulation to normal speed. Fast-forward and slow-motion intentionally change game timing and can also change how audio is presented while those modes are active.

Then try:

1. Open **Settings → Audio**.
2. Confirm **Enable sound** is on.
3. Set **Sound frequency** to **44100 Hz** for the normal high-quality setting.
4. Fully close and reopen the game.
5. Test another ROM to see whether the problem is game-specific.

If the problem only happens during fast-forward or slow-motion, mention the exact speed setting when reporting the bug.

---

## On-screen controls are misplaced, too large, or hard to press

Retra stores separate portrait and landscape layouts.

Open **Settings → Video → Screen size** to enter the Screen Editor. Rotate the device to edit the orientation you want.

In the editor you can:

- Drag controls to reposition them.
- Select a control and use the scale handle to resize it.
- Move or resize the game screen.
- Add supported controls with the **+** button.
- Use **Reset layout** if a layout has become unusable.

You can also manage layout profiles from **Settings → Layouts**.

---

## Saves or save states are missing

Do not immediately overwrite the game with a new save.

Try the following:

1. Confirm you imported the same game/ROM content you previously used.
2. Check whether the ROM was patched or replaced with a different revision.
3. Open **Settings → Misc → Open app folder** and verify that Retra's data is still present.
4. If you are moving data from another Retra folder, use **Settings → Misc → Import saves**.
5. If you previously created a `.retra` backup, use **More → Data storage → Restore backup**.

Removing a game from the Library is designed to be non-destructive. Permanent game-data deletion is a separate action.

---

## Create Backup or Restore Backup is not working

Retra v1.0.1 supports portable `.retra` backup files.

### Creating a backup

Go to **More → Data storage → Create backup**, choose the data you want to include, and save the `.retra` file using Android's file picker.

If saving fails:

- Choose a normal user-accessible folder such as Downloads or Documents.
- Make sure the device has free storage space.
- Try a different folder/provider in Android's file picker.

### Restoring a backup

Go to **More → Data storage → Restore backup** and select a valid Retra `.retra` file.

Remember that `.retra` backups do **not** contain ROM or BIOS files. You may need to re-import your ROMs separately after restoring Retra data on another device.

---

## Cheats do not work or stay enabled unexpectedly

1. Go to **Settings → Misc** and make sure **Enable cheats** is enabled.
2. Open the in-game menu and select **Cheats**.
3. Check that the cheat code matches the correct game, region, and revision.
4. Disable the cheat and reopen the game if the game's state does not immediately return to normal.

Some cheats permanently modify values inside a game's own save or runtime state. Turning the cheat off cannot always undo changes the game has already saved.

Cheats are unavailable during Local Link sessions.

---

## BIOS problems

Retra can run without a user-supplied BIOS for normal emulation.

If you choose to use one:

1. Open **Settings → Advanced**.
2. Enable **Use BIOS**.
3. Select the correct BIOS file for GBA, GB, or GBC.

If a game stops working after enabling BIOS support, disable **Use BIOS** and test again. Retra does not provide BIOS files.

---

## Artwork is missing or incorrect

Automatic artwork can require an internet connection.

If artwork is not loading:

- Check your network connection.
- If **Artwork on Wi-Fi only** is enabled under **Settings → Misc**, connect to Wi-Fi or disable that restriction.
- Try refreshing or re-importing the game if its metadata changed.
- Manual artwork overrides should take priority over automatic artwork.

---

## Local Link, Wi-Fi Remote Link, or Bluetooth Remote Link is not working

For multiplayer troubleshooting:

- Confirm that both players are using compatible game versions.
- Make sure both games support the normal GBA Link Cable mode Retra currently implements.
- For Wi-Fi Remote Link, keep both devices on a stable network and avoid VPNs or restrictive hotspot/network isolation when testing.
- For Bluetooth Remote Link, make sure Android Bluetooth permissions are granted and Bluetooth is enabled on both devices.
- Keep both devices awake and Retra in the foreground during connection testing.
- Return speed controls to normal before starting a Link session.

Retra v1.0.1 does not support GBA Single-Pak/Multiboot or Wireless Adapter/RFU emulation.

---

## Google Drive sync problems

If Drive sync is enabled but does not behave as expected:

1. Open **Settings → Misc**.
2. Verify **Sync to Google Drive** is enabled.
3. Confirm that the intended Google account is selected.
4. Check that the device has an internet connection.
5. Temporarily disable and re-enable sync if authorization was interrupted.

Before troubleshooting cloud synchronization, creating a local `.retra` backup is recommended.

---

## Retra crashes or closes unexpectedly

Try to reproduce the crash once with the simplest configuration possible:

- CPU profile: **Automatic**
- Frameskip: **0**
- GLSL shader: **None**
- Normal speed: **1×**
- BIOS: disabled unless required for the test

If the crash still happens, report it on the Retra GitHub repository under **Issues**.

Include:

- Retra version, for example `v1.0.1`
- Android version
- Device model
- Whether the issue happens in portrait, landscape, or both
- Game system: GB, GBC, or GBA
- Whether the ROM is patched or modified
- Exact steps that reproduce the problem
- Settings that may be relevant
- Screenshots or a short screen recording when useful
- The exact error message, if Android shows one

Do **not** upload copyrighted ROMs, BIOS files, personal save data, account credentials, or private keys to a public GitHub issue.

---

## Still having a problem?

Before opening a new GitHub issue, search existing issues to see whether the same problem has already been reported.

When creating a new issue, use a short descriptive title such as:

```text
Audio crackling at 2× fast-forward on Android 15
```

instead of:

```text
Retra broken
```

Clear reproduction steps make bugs much easier to diagnose and fix.
