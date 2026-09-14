# Fast-forward availability fix v1.0.2 — TDD evidence

## Scope
Fix a false-disabled fast-forward state caused by a stale Android-side `localLinkActive` flag while preserving the real Local/Remote Link speed restriction.

## RED
Command:
`node --test tests/fast-forward-availability-regression-v102.test.cjs`

Result before implementation: 4 failing checks (reconciliation helper absent, toggle trusted raw flag, menu trusted raw flag, hold-mode bypassed confirmed link state).

## GREEN
Command:
`node --test tests/fast-forward-availability-regression-v102.test.cjs tests/architecture-modularity.test.cjs`

Result: 9/9 passed.

Full regression command:
`node --test tests/*.test.cjs`

Result: 348/348 passed.

## Build verification
Attempted:
`./gradlew :app:compileDebugKotlin --no-daemon`

The build could not start in this sandbox because Gradle 9.6.0 was not cached and outbound network access is disabled (`UnknownHostException: services.gradle.org`). No compile PASS is claimed.

## Guarantees
- Normal gameplay re-checks the native mGBA link state before blocking fast-forward.
- A stale Java/Kotlin link flag is healed when the native link has ended.
- A real Local Link or active Remote Link still blocks speed changes.
- Press-to-toggle and hold-to-activate paths use the same confirmed restriction.
- Existing fast-forward timing/smoothness tests remain green.
