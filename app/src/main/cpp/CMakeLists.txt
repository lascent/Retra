cmake_minimum_required(VERSION 3.22.1)

project("retra")

# ============================================================
# mGBA CONFIGURATION
# ============================================================

# Build mGBA as a library for Retra.
set(LIBMGBA_ONLY ON CACHE BOOL "" FORCE)

# Enable Game Boy Advance + Game Boy cores.
set(M_CORE_GBA ON CACHE BOOL "" FORCE)
set(M_CORE_GB ON CACHE BOOL "" FORCE)

# ============================================================
# THREADING / LOCAL LINK
# ============================================================
#
# Retra Local Link uses:
#
#   mCoreThread
#   mLockstepThreadUser
#   GBASIOLockstepCoordinator
#
# mGBA's threading.h automatically defines DISABLE_THREADING
# if USE_PTHREADS is missing.
#
# Android uses POSIX pthreads, so threading must stay enabled.
#

set(
        DISABLE_THREADING
        OFF
        CACHE BOOL
        "Enable mGBA threading for Retra Local Link"
        FORCE
)

# ============================================================
# ADD mGBA
# ============================================================
#
# Folder structure:
#
# AndroidStudioProjects/
# ├── Retra/
# │   └── app/
# │       └── src/main/cpp/
# │
# └── mgba/
#

# Prefer a project-local checkout for reproducible builds, while remaining
# compatible with the older sibling-folder layout. RETRA_MGBA_SOURCE_DIR can
# always override both locations from Android Studio/CMake.
set(RETRA_MGBA_BUNDLED_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/mgba")
set(RETRA_MGBA_SIBLING_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../mgba")
if(EXISTS "${RETRA_MGBA_BUNDLED_DIR}/CMakeLists.txt")
    set(RETRA_MGBA_DEFAULT_DIR "${RETRA_MGBA_BUNDLED_DIR}")
else()
    set(RETRA_MGBA_DEFAULT_DIR "${RETRA_MGBA_SIBLING_DIR}")
endif()
set(RETRA_MGBA_SOURCE_DIR "${RETRA_MGBA_DEFAULT_DIR}"
    CACHE PATH "Path to the mGBA source checkout")
if(NOT EXISTS "${RETRA_MGBA_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "mGBA source is missing. Put a compatible checkout in third_party/mgba, "
        "keep the legacy sibling mgba folder beside Retra, or set RETRA_MGBA_SOURCE_DIR. "
        "See README.md.")
endif()
add_subdirectory(
        "${RETRA_MGBA_SOURCE_DIR}"
        "${CMAKE_CURRENT_BINARY_DIR}/mgba"
)

# ============================================================
# RETRA NATIVE LIBRARY
# ============================================================

add_library(
        emulator
        SHARED
        native-lib.cpp
)

# ============================================================
# RETRA COMPILE DEFINITIONS
# ============================================================

target_compile_definitions(
        emulator
        PRIVATE

        # Required by Retra / mGBA.
        ENABLE_VFS
        ENABLE_DIRECTORIES

        # IMPORTANT:
        # Prevent mgba-util/threading.h from automatically
        # defining DISABLE_THREADING.
        USE_PTHREADS
)

# ============================================================
# ANDROID LIBRARIES
# ============================================================

find_library(
        log-lib
        log
)

# ============================================================
# LINK LIBRARIES
# ============================================================

target_link_libraries(
        emulator

        # mGBA emulator core.
        mgba

        # Android logging.
        ${log-lib}
)

# Google Play / Android 15+ native compatibility: keep the final shared object
# aligned for devices that use 16 KB memory pages. NDK r28+ also enables this
# by default; the explicit linker option keeps the requirement visible here.
target_link_options(
        emulator
        PRIVATE
        "-Wl,-z,max-page-size=16384"
)
