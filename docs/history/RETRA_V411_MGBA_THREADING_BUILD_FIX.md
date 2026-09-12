# Retra v4.11 — mGBA Local Link threading build fix

The Local Link implementation uses `mLockstepThreadUser`, which mGBA only exposes when `DISABLE_THREADING` is not defined.

This build forces mGBA threading on before `add_subdirectory(mgba, ...)`:

- `DISABLE_THREADING=OFF`
- `USE_PTHREADS=ON`

After replacing the project, Android Studio should do **Build > Clean Project** and then rebuild. If Android Studio still shows the old generated flag, close the project and delete `app/.cxx` before rebuilding so CMake regenerates mGBA's `flags.h`.
