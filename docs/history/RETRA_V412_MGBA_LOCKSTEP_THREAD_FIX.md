# Retra v4.12 — mGBA Lockstep Thread Build Fix

This revision fixes the Android NDK compile error:

`unknown type name 'mLockstepThreadUser'`

Changes:
- Clears both normal and cached `DISABLE_THREADING` CMake values before mGBA is configured.
- Forces pthread support for Android/mGBA.
- Adds `-UDISABLE_THREADING` to both the `mgba` and Retra `emulator` native targets so stale/global defines cannot hide the lockstep thread API.
- Links both native targets with CMake `Threads::Threads`.
- Loads mGBA's generated `flags.h` first in `native-lib.cpp`, then explicitly removes a stale `DISABLE_THREADING` macro before including threaded lockstep headers.
- Uses the explicit `struct mLockstepThreadUser` spelling at the C/C++ boundary.

After replacing the project, delete `Retra/app/.cxx` once, then Sync/Rebuild so Android Studio regenerates mGBA's `flags.h` and native build cache.
