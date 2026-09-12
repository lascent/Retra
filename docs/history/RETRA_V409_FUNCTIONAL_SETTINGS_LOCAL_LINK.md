# Retra v4.09 — Functional Settings + Experimental Local Link

This build merges the user's `Retra_v4.07_Experimental_Local_Link_PATCH` into the v4.08 Functional Settings project without replacing the v4.08 settings work.

## Link Local

- Real same-device **GBA** link implementation from the supplied experimental patch is included.
- Uses two synchronized mGBA cores and mGBA's native GBA SIO lockstep coordinator.
- In the in-game menu, choose **Link local** and either:
  - open the current GBA game as Player 2, or
  - choose another GBA ROM.
- **Switch player** swaps Retra's visible framebuffer and controls between P1 and P2 while both cores keep running.
- Same-ROM multiplayer uses the second-player save suffix behavior from the supplied patch.
- Save states, cheats, and fast-forward stay disabled during Local Link to reduce desync risk.
- v4.08 runtime core settings and audio-buffer configuration are also applied to the two link cores.

### Local Link limitations

- GBA only; GB/GBC cable emulation is not added by this patch.
- Runtime-patched sessions are blocked in Local Link. A ROM hack that already exists as its own `.gba` file is fine.
- Starting a Local Link session creates a fresh synchronized pair of cores, matching the behavior documented by the supplied patch.
- This still needs real-device testing against the user's local mGBA source tree.

## Link Remote

**Remote Link is not implemented in the supplied patch.**

The Wi-Fi/Bluetooth menu remains visible but is explicitly labeled as not implemented. Real phone-to-phone GBA multiplayer needs a networked SIO/lockstep transport with timing, handshake, latency buffering, disconnect handling, and resynchronization. A normal socket that merely says "connected" would not make trading/battling work correctly.

## Preserved from v4.08

- Google Drive sync
- Import saves
- Open app folder/export
- Auto Save & Load
- ROM patching
- CPU Core profiles
- BIOS options
- Frame skip/video settings
- AudioTrack sound output
- Fast-forward button modes
- Confirm close/reset
- Existing library, layout, controller, statistics, save-state, and UI work
