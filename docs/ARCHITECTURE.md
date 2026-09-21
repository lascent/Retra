# Retra Architecture

Retra is split into four major layers so gameplay timing, Android UI, storage, and WebView presentation can evolve independently.

```text
Web UI (HTML/CSS/JS)
        |
        v
WebUiController / SettingsController / RomUiController
        |
        v
MainActivity (lifecycle + composition root)
        |
        +---------------------+----------------------+------------------+
        |                     |                      |                  |
        v                     v                      v                  v
Gameplay runtime        Persistence/data       Multiplayer        Platform services
        |                     |                      |                  |
        v                     v                      v                  v
EmulationSessionManager  SaveDataRepository     RemoteLinkTransport  GoogleDriveApiRepository
GameplayController       SaveStateRepository    MultiplayerController ArtworkRepository
GameplayLayoutController RomIdentityStore       GbaMultiplayer...     AppUpdateController
GameplayTouchController  BackupRepository
ControllerFeedbackManager
        |
        v
Native mGBA bridge / AudioController / frame presenter
```

## Composition root

`MainActivity` is intentionally kept as an Android lifecycle/composition root. It owns wiring, launchers, top-level state and JNI entry points, but feature implementations live in focused controllers and repositories.

Architecture guardrails enforce:

- `MainActivity.kt` stays below 1,500 lines.
- activity-scale controllers stay bounded instead of folding back into the Activity.
- Web UI JavaScript and CSS remain feature-split.
- controller feedback policy is isolated from pointer ownership.
- ROM/import domain models are top-level immutable models instead of nested Activity types.

## Gameplay input and feedback

`GameplayTouchController` owns pointer ownership, D-pad hysteresis, key press/release semantics and low-latency multi-touch behavior. It performs input first and feedback second.

`GameplayInputState` owns the mutable D-pad pointer, key mask, multi-source key-hold counts, and generation counter shared by touch/layout/link input paths. This keeps pointer bookkeeping out of Activity lifecycle state.

`ControllerFeedbackManager` owns all optional controller sound and haptic policy. It uses Android system-tuned haptics rather than raw vibrator pulses and applies independent short rate limits to avoid stacked buzzing or loud multi-touch clicks.

The feedback profile is intentionally consistent across D-pad, A/B, L/R, Start/Select and utility controls. Both controller sound and controller haptics are independently user-configurable.

## Domain models

`RetraModels.kt` contains immutable shared models such as `NativeLibraryItem`, `PendingPatchLaunch`, and `PendingLocate`. Controllers exchange these models directly rather than depending on nested `MainActivity` types.

## Gameplay presentation

`EmulationSessionManager` owns emulator-thread lifecycle and exact speed scheduling. Video presentation is intentionally decoupled from emulation throughput.

`GameplayFrameMailbox` is the producer/consumer boundary between mGBA and rendering. In v1.0.4 it maintains four permanently preallocated pixel buffers: producer, newest pending, GL-owned rendering, and spare. Producer and renderer result holders are reused on the gameplay hot path. If another emulated frame completes before rendering, the older pending frame is recycled instead of queued; if an unexpected pool invariant is violated, Retra drops the visual publish rather than allocating during a GPU stall. The renderer therefore cannot accumulate a fast-forward backlog or trigger normal hot-path framebuffer allocation.

`GameplayFramePresenter` samples the newest available frame on Android Choreographer/VSync and asks `ShaderGameView` to render. It coalesces real mailbox generations with CAS updates, reuses posted Runnables, and resets generation state at each session start so a new ROM cannot inherit stale presentation state. `ShaderGameView` owns the OpenGL render thread, reuses a mutable mailbox frame holder, and consumes the mailbox directly. Normal no-shader gameplay uses the same pass-through GL surface; the legacy `ImageView` exists only as a compatibility fallback.

`DisplayPerformanceManager` keeps gameplay on a stable cadence-compatible mode up to 120 Hz. Speed Mode changes the emulation multiplier, not the panel mode. This avoids compositor mode-switch hitches and keeps 2×/4×/8×/16× timing independent from presentation FPS.

`AudioController` owns Android audio output and its dedicated writer thread. Potentially blocking `AudioTrack` writes do not run on the emulation thread, and audio processing is not allowed to delay a completed video-frame publication. Turbo audio uses the native speed-aware FIR path, and v1.0.4 resets audio transform state immediately when the selected speed changes so previous-rate PCM cannot leak into the first slice at the new multiplier.

## Persistence

Retra separates ROM content from persistent user data. ROM identity is content-hash based and persistent data is handled through dedicated repositories:

- `RomIdentityStore` — Room-backed ROM identity and metadata.
- `SaveDataRepository` — battery saves and atomic save handling.
- `SaveStateRepository` — save states and state metadata.
- `BackupRepository` — `.retra` backup/restore.
- `RetraPreferences` — DataStore-backed global preferences.
- `GameplayLayoutRepository` — controller/screen layout persistence.

## Web UI boundary

The packaged WebView interface is a presentation layer. Native state crosses through `WebUiController`/bridge methods; storage and gameplay behavior remain native responsibilities. JavaScript and CSS are split by feature to keep the Web UI maintainable and deterministic.

## Multiplayer boundary

`MultiplayerController` owns UI/session coordination. `RemoteLinkTransport` owns network transport/timing state. Local native link behavior remains behind JNI. This keeps network protocol logic out of `MainActivity`.

## Testing and architecture regression

`python3 tools/release_gate.py` runs JavaScript syntax checks, dependency-light regression tests and release-structure validation. Architecture tests verify module boundaries and size limits so future feature work cannot silently re-create monolithic controllers.

Android-specific validation should additionally run Kotlin compilation, unit tests, lint, and physical-device smoke tests.
