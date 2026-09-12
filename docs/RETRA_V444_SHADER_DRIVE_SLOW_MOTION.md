# Retra v4.44 — GLSL shaders, Google Drive API sync, and slow motion

## GLSL shaders
- Optional OpenGL ES 2.0 fragment-shader renderer.
- Built-ins: None, LCD Grid, CRT Lite, Grayscale.
- Custom `.glsl`, `.frag`, `.fs`, `.fsh` installation with validation and size limits.
- `RENDERMODE_WHEN_DIRTY`, reusable vertex buffers, and texture sub-updates avoid continuous/avoidable GPU work.
- Selecting None keeps Retra's normal lightweight game-screen renderer.

## Google Drive sync
- Prefers Google Drive REST v3 using an account OAuth `drive.file` token when Android can authorize it for the installed app.
- Uses an app-created Retra Sync folder, SHA-256 verification, sync state, conflict backups, and delete tombstones.
- Falls back to the existing Storage Access Framework Drive-folder sync when direct Drive authorization is unavailable.
- No OAuth client secret is embedded in the APK. Production distribution should register the app package/signing certificate with the Google Cloud OAuth configuration used for the release build.

## Emulation speed
- Supported choices: 0.2×, 0.5×, 1×, 2×, 4×, 8×, 16×.
- Slow motion stretches emulator frame cadence instead of changing display refresh policy.
- Turbo executes multiple emulated frames per native cadence.
- Existing 60/90/120 Hz adaptive display presentation remains independent from game speed.
