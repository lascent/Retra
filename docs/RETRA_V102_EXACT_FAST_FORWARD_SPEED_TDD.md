# Retra v1.0.2 Exact Fast-Forward Speed Contract — TDD Evidence

## Goal
Make each fast-forward preset represent the same multiplier for every GBA ROM:
1x=1.00x, 2x=2.00x, 4x=4.00x, 8x=8.00x, 16x=16.00x relative to that ROM's own 1x emulated time.

## Root cause found
The previous build initially left mGBA renderer frameskip at the user's normal value (normally 0). At 8x that can ask the core to render about 477.82 internal GBA frames/s, even though Android can only use roughly 60-120 unique presented states/s. Heavier ROMs could therefore become renderer-bound and fall below the selected multiplier while lighter ROMs still reached it.

## RED evidence
Command:
`node --test tests/exact-fast-forward-contract-v102.test.cjs`

Result before production changes: 0/4 passed. The current implementation had no speed-protecting renderer budget and no shared exact-speed contract.

## GREEN evidence
The same test after implementation: 4/4 passed.

The pure Kotlin speed contract was compiled and executed directly. A separate deterministic performance-monitor harness also proved that an 8x run achieving only about 7.57x enters constrained protection within 500 ms and recovers after sustained ~7.97x throughput. Measured mathematical targets using Retra's GBA frame time (16,742,706 ns):
- 1x: 59.727502 emulated fps, renderer skip 0 at 120 Hz
- 2x: 119.455003 emulated fps, renderer skip 0 at 120 Hz
- 4x: 238.910007 emulated fps, renderer skip 1 at 120 Hz
- 8x: 477.820013 emulated fps, renderer skip 3 at 120 Hz
- 16x: 955.640026 emulated fps, renderer skip 7 at 120 Hz

All CPU/game frames still execute. Renderer skipping only removes internal video work the display cannot use.

## Regression verification
Command:
`node --test tests/*.test.cjs`

Result: 353/353 passed, 0 failed, 0 skipped.

A focused Kotlin compile of `FastForwardSpeedContract.kt`, `TurboFramePolicy.kt`, and `TurboThroughputGovernor.kt` also passed using an Android `Process` test stub.

## Android build limitation
`:app:compileDebugKotlin --offline` could not run in this sandbox because the Gradle 9.6 distribution is not cached and outbound network access is unavailable. This is an environment/tooling limitation, not a claimed Android build pass. The project should still be compiled on the user's Android Studio environment before release.

## Behavioral guarantee
The runtime governor now derives wall-clock deadlines from one shared emulated-time contract, while renderer work is budgeted independently. This avoids ROM-specific fast-forward tuning and preserves emulator logic/timing semantics.

A device still needs enough CPU capacity to execute the actual emulated workload. If a device cannot execute a particular ROM at 8x even after renderer/audio work is reduced, software cannot truthfully manufacture 8x without dropping emulated game logic, which this fix intentionally does not do.
