# Retra v1.0.1 — focused hardening plan

This pass closes the remaining source-level release gaps without redesigning Retra.

## 1. BIOS portable-backup state safety

BIOS binaries are intentionally not portable. Their filesystem paths, **Use BIOS** state,
**Boot BIOS** state, and the last BIOS label now remain device-local. New portable
settings snapshots omit them, and restore also filters those keys from older backups.
A backup therefore cannot leave a new device showing/enabling a BIOS that is absent.

## 2. High-frequency settings sliders

Controller opacity, Frameskip, Sound volume, and Link sync check use a specialized
range bridge. Web labels/previews remain immediate while native updates are rate-limited.
During a drag Retra touches only the affected runtime subsystem; the portable metadata
refresh happens once on the final committed slider value.

Targets:
- Volume: 48 ms native update interval.
- Controller opacity: 64 ms.
- Frameskip: 72 ms.
- Link sync check: 96 ms.

## 3. Reproducible mGBA source

Retra is locked to mGBA commit:

`543a197582c30364584d773a974d7f991892fa43`

`third_party/mgba.lock` is the source-of-truth. `tools/prepare_mgba.py` checks out that
exact commit. CMake refuses a different commit, a dirty tracked checkout, or an
unverifiable source tree.

## 4. Android instrumentation coverage

The device-side suite now covers more than ROM identity/migration:
- portable preferences and BIOS-state filtering;
- legacy portable-backup restore safety;
- verified atomic Android filesystem operations;
- existing Room ROM identity and migration behavior.

These complement the fast source/contract regression suite; they do not replace it.

## 5. Physical-device validation

Google Drive authorization/sync, Wi-Fi Link, Bluetooth Link, IME behavior, foldable
postures/resizing, and real refresh-rate behavior require actual Android hardware and
accounts/radios. `REAL_DEVICE_VALIDATION_MATRIX_v1.0.1.md` defines the release matrix,
expected results, and evidence to capture before tagging a release.
