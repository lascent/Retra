# Retra Development History

Retra's public stable versioning begins with **v1.0.0**. The sequence below organizes the major pre-1.0 development milestones into a conventional `0.x` progression.

These entries describe development milestones, not separate stable public releases.

## Version path

```text
v0.8.0 → v0.8.9
v0.9.0 → v0.9.12
v1.0.0  First stable public release
```

## v0.9 series — release preparation

| Version | Milestone |
|---|---|
| **v0.9.12** | Final Library polish, Screen Editor hardening, Remote Link health, and release gates |
| **v0.9.11** | MainActivity architecture refactor and compile hotfixes |
| **v0.9.10** | GBA multiplayer eligibility and Remote Link recovery hardening |
| **v0.9.9** | GLSL shaders, Google Drive REST sync, and slow motion |
| **v0.9.8** | Adaptive 60/90/120 Hz gameplay presentation |
| **v0.9.7** | Artwork reliability and Settings bottom-safe-area fixes |
| **v0.9.6** | Architecture/maintainability modularization |
| **v0.9.5** | Responsive ROM detail spacing |
| **v0.9.4** | Auto-close category selection after save |
| **v0.9.3** | Solid Remove from Library menu background |
| **v0.9.2** | Multi-select More-menu position refinement |
| **v0.9.1** | Android navigation-safe Settings scrolling |
| **v0.9.0** | Menu Close auto-save and safe exit pipeline |

## v0.8 series — core feature development

| Version | Milestone |
|---|---|
| **v0.8.9** | Multi-select popup positioning |
| **v0.8.8** | Performance architecture and high-refresh UI |
| **v0.8.7** | Unified dialog design polish |
| **v0.8.6** | Optimized automatic artwork |
| **v0.8.5** | Library multi-select responsiveness |
| **v0.8.4** | Pokémon Flash 1M compatibility |
| **v0.8.3** | mGBA ROM-hack savedata autodetection fix |
| **v0.8.2** | Library multi-select polish |
| **v0.8.1** | GBA ROM-hack save-memory compatibility |
| **v0.8.0** | Persistent ROM data foundation |

## v1.0.0 — stable

The first stable public release consolidates the pre-1.0 work into a release-ready package with:

- persistent ROM identity and protected game data;
- mGBA-backed emulation with GBA-focused features;
- Screen Editor/controller customization;
- Local/Wi-Fi/Bluetooth GBA link infrastructure;
- adaptive high-refresh UI presentation;
- artwork, Color Style, shaders, save states, cheats, speed controls, and cloud/folder sync;
- modular Android/WebView architecture;
- regression tests, release validation, CI, licensing, security policy, and release documentation.

Detailed public-facing changes are in the repository-root [`CHANGELOG.md`](../CHANGELOG.md).

## Archived engineering notes

The repository also contains older engineering notes whose filenames retain their original internal build identifiers. They are preserved as historical implementation references and should not be interpreted as the public release version sequence.

See [`history/`](history/) and the other engineering documents indexed in [`docs/README.md`](README.md).
