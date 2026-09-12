# Retra v3.96 — Gameplay Controller Icon Sync

## Fixed
The native gameplay overlay was using Android text/Unicode symbols while the Screen Size Editor used custom SVG controller icons. That made the controls look different once a ROM started.

## Changes
- Gameplay Menu now uses the same three-line SVG-style icon as the editor.
- Gameplay Quick Load now uses the same curved arrow icon as the editor.
- Gameplay Quick Save now uses the same floppy-disk outline icon as the editor.
- Gameplay Fast Forward now keeps the same double-chevron icon as the editor instead of changing into text.
- Gameplay D-pad now uses the same centered chevron design as the editor for all four directions.
- Start / Select now match the editor's clean pill appearance without duplicate text labels.
- Existing saved position, scale, orientation, opacity, and layout synchronization are preserved.

The Screen Size Editor and actual gameplay overlay now share the same controller visual language instead of using separate glyph systems.
