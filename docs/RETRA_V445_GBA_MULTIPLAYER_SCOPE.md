# Retra v4.45 — GBA multiplayer scope and recovery hardening

Supported multiplayer scope:

- GBA Local Link
- GBA Wi-Fi Remote Link
- GBA Bluetooth Remote Link

Single-Pak/Multiboot and Wireless Adapter/RFU are intentionally unsupported.

Automatic compatibility detection validates the current GBA platform, patch state, file type, cartridge header, internal title/game code, and header checksum. Unknown games remain technically eligible when they are valid unpatched GBA ROMs because the GBA header has no universal flag that declares in-game multiplayer support.

Remote Link retains deterministic state-hash checks and host snapshots, and now retries a stalled resync up to three times with input-schedule reseeding after successful recovery.
## v1.0.0 transport health hardening

Remote Link keeps the same supported GBA scope and protocol version, but the release pass strengthens transport behavior:

- RTT and RTT-jitter are tracked separately, with adaptive input delay derived from both;
- incoming input frames and key masks are sanity-checked before scheduling;
- overly-future input frames are rejected and late-input fallbacks are counted;
- disconnect delivery is one-shot so simultaneous reader/writer/watchdog failures cannot stack duplicate disconnect UI events;
- resync timeout/recovery and packet counters are exposed through a `HealthSnapshot`;
- gameplay UI surfaces connection quality, RTT, jitter, input delay, and recovery state for diagnostics.

This is reliability/observability hardening only. It does not add Single-Pak/Multiboot or Wireless Adapter/RFU emulation.

