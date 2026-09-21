# Retra Documentation

This directory contains release, architecture, validation, and historical engineering documentation for Retra.

## Current release

- [`RELEASE_NOTES_v1.0.4.md`](RELEASE_NOTES_v1.0.4.md) — user-facing release notes for Retra v1.0.4
- [`RETRA_V104_SMOOTH_TURBO_HARDENING.md`](RETRA_V104_SMOOTH_TURBO_HARDENING.md) — v1.0.4 frame-mailbox, VSync, pacing, and turbo-audio hardening
- [`RELEASE_NOTES_v1.0.0.md`](RELEASE_NOTES_v1.0.0.md) — user-facing release notes for Retra v1.0.0
- [`RELEASE_CHECKLIST_v1.0.0.md`](RELEASE_CHECKLIST_v1.0.0.md) — final publication and device-smoke-test checklist
- [`DEVELOPMENT_HISTORY.md`](DEVELOPMENT_HISTORY.md) — organized pre-v1.0 development version history
- [`RETRA_V102_UPDATE_REINSTALL_RECOVERY.md`](RETRA_V102_UPDATE_REINSTALL_RECOVERY.md) — v1.0.2 in-app update checking and uninstall/reinstall continuity
- [`RETRA_V101_HARDENING_PLAN.md`](RETRA_V101_HARDENING_PLAN.md) — BIOS portability, slider hot-path, mGBA pinning, instrumentation and device-validation hardening
- [`REAL_DEVICE_VALIDATION_MATRIX_v1.0.1.md`](REAL_DEVICE_VALIDATION_MATRIX_v1.0.1.md) — required physical-device/service validation matrix for v1.0.1

## Architecture and validation

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — current application architecture, module boundaries, and regression guardrails
- [`CONTROLLER_FEEDBACK.md`](CONTROLLER_FEEDBACK.md) — controller haptic/sound feedback architecture and behavior
- [`PERSISTENT_STORAGE_ARCHITECTURE.md`](PERSISTENT_STORAGE_ARCHITECTURE.md) — persistent ROM identity, storage, migration, and recovery model
- [`STORAGE_UPGRADE_VALIDATION.md`](STORAGE_UPGRADE_VALIDATION.md) — storage migration/upgrade validation notes
- [`HOME_NAVIGATION_PERFORMANCE_FIX.md`](HOME_NAVIGATION_PERFORMANCE_FIX.md) — warm-return/Home navigation performance notes
- [`RETRA_V1_GAMEPLAY_SMOOTHNESS_OPTIMIZATION.md`](RETRA_V1_GAMEPLAY_SMOOTHNESS_OPTIMIZATION.md) — v1 gameplay presentation/smoothness work

## Historical engineering notes

Older implementation notes are retained for maintainers and regression investigation. Some filenames contain legacy internal build identifiers. Those filenames are archival identifiers only; the organized pre-v1.0 public-facing development sequence is documented in [`DEVELOPMENT_HISTORY.md`](DEVELOPMENT_HISTORY.md).

The main archive is under [`history/`](history/). Additional implementation notes remain in this directory where they are referenced by engineering documentation or useful for regression analysis.

## Repository-level documents

- [`../README.md`](../README.md) — project overview, build instructions, and release summary
- [`../CHANGELOG.md`](../CHANGELOG.md) — public changelog
- [`../CONTRIBUTING.md`](../CONTRIBUTING.md) — contribution guide
- [`../SECURITY.md`](../SECURITY.md) — security policy
- [`../THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) — third-party licensing notices
- [`../LICENSE`](../LICENSE) — MPL-2.0 license
- `RETRA_V103_LATEST_FRAME_GPU_PRESENTATION.md` — v1.0.3 latest-frame GPU presentation foundation; superseded for current mailbox details by the v1.0.4 SmoothTurbo hardening document.
