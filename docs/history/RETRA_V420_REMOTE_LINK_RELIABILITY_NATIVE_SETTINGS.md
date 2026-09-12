# Retra v4.20 — Remote Link Reliability + Native Settings

## Remote Link v2 reliability

Remote Link now uses protocol `RETRA_REMOTE_LINK_V2` and adds recovery on top of the existing replicated two-core mGBA link session.

- Adaptive frame input delay (4–18 frames) based on measured round-trip latency.
- Heartbeats and ping/pong RTT monitoring.
- Periodic deterministic checkpoints from both linked mGBA cores.
- Host sends a combined state hash for a completed emulated frame.
- Client compares the same-frame hash and requests recovery on mismatch.
- Late frame-numbered input is no longer silently applied to the wrong frame; it triggers recovery.
- Excessive phone-to-phone frame drift or internal P1/P2 core skew triggers recovery.
- Host-authoritative paired state snapshots resynchronize both GBA cores together.
- Recovery pauses both linked cores, imports the same paired state on the client, then resumes both peers together.
- Short background/screen-sleep transitions send PAUSE/RESUME and resynchronize before gameplay continues.
- Connection timeout is suspended while a deliberate pause or state recovery is in progress.
- In-game Remote Link status now shows transport, role, RTT, and current input-delay frames.

A hard transport failure/process death still ends the session and requires reconnecting. TCP/RFCOMM are ordered reliable transports, so Retra treats very late data as a sync fault rather than trying to apply stale input.

## Different-ROM Remote Link

Remote Link is no longer restricted to the same cartridge on both players.

- P1 is always the host cartridge; P2 is always the client cartridge.
- Each phone exchanges SHA-256 cartridge identities.
- If the two games differ, Retra finds the peer cartridge in its own imported GBA library.
- Both cartridge ROMs must already be imported on both phones.
- Retra does not transfer ROM files over the link.
- Temporary mirrored battery saves are backed up and restored after the link session.
- Same-ROM sessions keep the normal `.sav` / `.sa2` P1/P2 behavior.

Single-Pak multiboot and GBA Wireless Adapter/RFU are still outside this implementation.

## Cartridge Save Type

`Settings > Advanced > Cartridge save type` now directly controls mGBA's GBA savedata implementation instead of only storing the UI value.

Supported values:

- Automatic
- EEPROM
- SRAM
- Flash 64K
- Flash 128K
- None

Switching back to Automatic also removes a previously forced save-type override.

## Mosaic Effect

The Mosaic Effect toggle is now connected to the native GBA video renderer.

- Enabled: the game's MOSAIC register is rendered normally.
- Disabled: the renderer receives a zero mosaic value while the emulated game-visible MOSAIC register remains intact.
- The setting is applied to normal gameplay and both linked cores.

## CPU profile

The old `CPU core` label was misleading because Retra's current mGBA integration does not switch between separate runtime interpreter/dynarec backends.

It is now named `CPU profile` and continues to control Retra's real performance/compatibility behavior such as Android emulation-thread priority and mGBA idle-loop optimization.

Profiles:

- Automatic
- Performance
- Compatibility

## Link sync check

The old `SMC check` slider was not connected to an actual mGBA self-modifying-code backend option. It is now a functional `Link sync check` control.

- Lower values perform Remote Link state-integrity checkpoints more often.
- Higher values reduce checkpoint frequency and CPU overhead.
- Default remains 4.

## Compatibility notes

- Remote Link v2 requires both devices to run Retra v4.20 or another build using the same `RETRA_REMOTE_LINK_V2` protocol.
- Different-game linking requires the exact two cartridge ROMs to be locally available on both phones.
- A real socket loss, force-stop, or process death cannot be repaired from RAM and requires reconnecting.
- Existing Local Link threading/CMake configuration from the confirmed-working v4.13+ build is preserved.
