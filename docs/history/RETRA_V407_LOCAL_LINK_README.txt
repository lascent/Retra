Retra v4.07 - Experimental GBA Local Link
=========================================

What this patch adds
--------------------
- Real same-device GBA Link Local backend using mGBA's native
  GBASIOLockstepCoordinator / GBASIOLockstepDriver APIs.
- Two synchronized mGBA cores run at the same time.
- Player 1 and Player 2 can be switched from Retra's in-game menu.
- The active player gets Retra's controls and framebuffer; the other core
  keeps running so link-cable timing is not stopped.
- Normal .sav battery saves are attached to GBA cores. If the SAME ROM is
  opened for both players, Player 2 uses mGBA's .sa2 multiplayer save suffix.
- Save states, cheats, and fast-forward are disabled while Local Link is
  active to reduce desync risk.

How to test on Android Studio
-----------------------------
1. Back up your current Retra project.
2. Make sure the sibling mGBA source folder is still here:

   AndroidStudioProjects/
     Retra/
     mgba/

3. Open Retra in Android Studio and Sync/Build normally.
4. Run on your Android phone.
5. Open a GBA game.
6. Open Retra's in-game Menu -> Link local.
7. Choose either:
     - the current game (a second instance), or
     - Another game... (a second GBA ROM).
8. When connected, use Menu -> Switch player to alternate between P1/P2.
9. Put both games into their normal GBA cable/link room and test a trade or
   battle.
10. Use Menu -> Disconnect local link to return to normal single-core play.

Important current limitations
-----------------------------
- GBA only. This patch does not add GB/GBC cable emulation.
- Starting Local Link starts a fresh synchronized pair of mGBA cores. Use the
  game's normal in-game save (.sav) before entering Local Link if you need
  existing progress.
- Patched-at-runtime ROM sessions are blocked for Local Link in this first
  build. A ROM hack that is already a standalone .gba file is fine.
- Remote Link (phone-to-phone Wi-Fi/Bluetooth) is NOT implemented in this
  patch. Retra keeps the existing Remote Link menu as an honest placeholder.
  Upstream mGBA provides local lockstep but does not provide a finished
  networked GBA-to-GBA link transport in this embedded API.

mGBA compatibility target
-------------------------
This patch was written against the mGBA build metadata found in the uploaded
Retra project:
  0.11-9135-543a19758
  commit 543a197582c30364584d773a974d7f991892fa43

The uploaded ZIP did not include the sibling mgba/ source directory, so the
native code could not be rebuilt inside the ChatGPT sandbox. The required
symbols were checked against the prebuilt libmgba.a contained in your full
Retra ZIP, and Kotlin/JNI method names were cross-checked.

If Android Studio reports a compile/build error, send the exact Build Output
and it can be patched against your local mGBA source tree.
