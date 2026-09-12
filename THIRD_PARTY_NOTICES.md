# Third-Party Notices

Retra uses and/or interoperates with third-party open-source software. This notice is intended to make the most important ownership and licensing boundaries explicit. It does not replace the license files shipped by each dependency.

## mGBA

Retra's native emulator integration is built around the mGBA emulator core.

- Project: mGBA
- Upstream: https://github.com/mgba-emu/mgba
- Copyright: mGBA contributors, including Jeffrey Pfau
- License: Mozilla Public License 2.0 (MPL-2.0)

When building or redistributing Retra with mGBA, retain the mGBA license and notices from the exact source revision used for the build. Retra does not claim ownership of mGBA.

## Android / Jetpack / Material dependencies

Retra also depends on AndroidX, Material Components, Kotlin/coroutines, Room, DataStore, WebKit, and related Android build/runtime libraries. These packages retain their own copyright notices and licenses. Gradle resolves the exact dependency versions declared in `gradle/libs.versions.toml` and `app/build.gradle.kts`.

## User-supplied game content

Retra does not include commercial ROMs, BIOS images, game artwork, or other copyrighted game data. Those files remain the property of their respective rights holders. Users are responsible for using content they are legally permitted to use.
