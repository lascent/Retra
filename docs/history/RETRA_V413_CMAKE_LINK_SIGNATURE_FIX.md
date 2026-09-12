# Retra v4.13 — CMake mGBA link signature fix

The mGBA project already calls `target_link_libraries(mgba ...)` using CMake's plain signature.
Retra v4.12 added `target_link_libraries(mgba PRIVATE Threads::Threads)`, which mixed keyword and plain signatures and caused CMake configuration to fail.

This version changes it to:

```cmake
target_link_libraries(mgba Threads::Threads)
```

so every call for the `mgba` target uses the same plain signature. The threading/lockstep fixes from v4.12 are preserved.

After replacing the project, delete `app/.cxx`, sync Gradle, then rebuild.
