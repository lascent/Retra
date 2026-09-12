# Native third-party sources

Retra prefers a compatible mGBA checkout at `third_party/mgba/` so the project can be kept self-contained in a normal repository checkout.

For compatibility with older Retra setups, CMake still falls back to a sibling `../mgba/` directory. You can also set `RETRA_MGBA_SOURCE_DIR` explicitly.

Do not replace the mGBA revision used by a known-good Retra build without testing battery saves, save states, GB/GBC, GBA, Local Link and Remote Link.


## Licensing

mGBA is a separate upstream project distributed under the Mozilla Public License 2.0 (MPL-2.0). Keep the upstream `LICENSE` file and notices with the exact mGBA revision used for builds and redistribution. See the repository-root `THIRD_PARTY_NOTICES.md`.
