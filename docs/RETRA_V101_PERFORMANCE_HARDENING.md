# Retra v1.0.1 Performance Hardening

This pass improves runtime responsiveness without changing mGBA's native game timing.

## Gameplay presentation

Retra now uses three framebuffer owners: an mGBA write buffer, a newest-completed publish buffer, and a UI presentation buffer. The VSync presenter claims the newest frame with a pointer swap under `frameLock`, then performs `Bitmap.setPixels()` or the OpenGL shader upload after releasing the lock. This prevents a slow Android UI/GPU upload from blocking the emulator thread.

The emulator remains paced at the GBA cadence (~59.73 FPS). 90/120 Hz Android displays only improve presentation latency/cadence; they do not make the emulated game run faster.

## High-frequency settings

Range controls continue to preview immediately through the specialized bridge, but drag previews no longer persist DataStore state for every pointer movement. Controller opacity, frame skip, volume, and SMC/sync-check settings are persisted only when the range gesture commits. The final commit still queues the portable settings-folder sync.

This reduces allocations and storage work while sliders are moving and keeps Settings scrolling/interaction responsive.

## Background storage priority

Retra's serialized storage executor now runs with Android `THREAD_PRIORITY_BACKGROUND` and a slightly lower Java thread priority. Import/export, portable-folder maintenance, and other serialized disk work therefore yield CPU time to live emulation and audio. Network/link work remains at normal priority so multiplayer latency is not intentionally degraded.

## Validation

The dependency-light release gate and the dedicated performance-hardening regression checks must pass before packaging this build. Real-device validation at 60/90/120 Hz remains required because source tests cannot prove device-specific scheduler, GPU, audio-route, or thermal behavior.
