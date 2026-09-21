package com.retra.emulator

/**
 * Compatibility stub for older Retra worktrees that may still contain the
 * retired gameplay diagnostics source file.
 *
 * Runtime performance telemetry now lives in the dedicated pacing,
 * presentation, and turbo monitor components. Keeping this empty object makes
 * copy-over upgrades safe without reintroducing references to removed APIs.
 */
internal object GameplayPerformanceDiagnostics
