# Native third-party sources

Retra is pinned to one validated mGBA source revision for reproducible native builds.
The lock file is `third_party/mgba.lock`.

Prepare the preferred project-local checkout with:

```bash
python3 tools/prepare_mgba.py
```

Retra currently requires:

`543a197582c30364584d773a974d7f991892fa43`

CMake still accepts the legacy sibling `../mgba/` directory or an explicit
`RETRA_MGBA_SOURCE_DIR`, but it verifies that the checkout resolves to the exact locked
commit and has no tracked local modifications. A verified source archive may provide a
`.retra-mgba-revision` marker containing the same full SHA.

Do not update `mgba.lock` casually. A revision change requires battery-save, save-state,
GB/GBC/GBA, audio, BIOS, Local Link and Remote Link validation before it becomes Retra's
new lock.

## Licensing

mGBA is a separate upstream project distributed under the Mozilla Public License 2.0
(MPL-2.0). Keep the upstream `LICENSE` file and notices with the exact mGBA revision used
for builds and redistribution. See the repository-root `THIRD_PARTY_NOTICES.md`.
