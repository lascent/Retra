# Retra v1.0.2 — Professional shader pack

Retra's existing optional OpenGL ES 2.0 shader path now includes a curated built-in preset pack instead of shipping only the default renderer.

## Presets

- **Off** — normal Retra renderer; no shader-view GPU cost.
- **GBA Color Corrected** — restrained handheld-style color response.
- **Sharp** — light unsharp sampling for crisp pixel edges.
- **Smooth** — low-cost multi-sample smoothing for uneven scaling.
- **Pixel Perfect** — snaps sampling to original source-pixel centers.
- **LCD Grid** — subtle handheld LCD cell structure.
- **LCD Response** — gentle spatial LCD-response softness.
- **Scanlines** — lightweight retro horizontal scanline texture.
- **CRT Lite** — restrained curvature, scanlines and vignette.
- **Retro Warm** — warm handheld-style palette treatment.

Each preset is labeled **None**, **Low**, or **Medium** GPU impact in the Video settings picker. Custom GLSL installation remains supported.

## Performance hardening

The shader renderer remains `RENDERMODE_WHEN_DIRTY`, so it only renders when a game frame is submitted. Program attribute/uniform locations are cached when a shader program is linked instead of queried every displayed frame, and texture filtering state is only updated when the filtering preference actually changes.

This works with Retra's turbo-frame publication policy: 4x/8x/16x emulation may advance many hidden core frames, but the shader only runs for frames Retra actually presents to the display.

## Compatibility

All built-in presets use the same constrained OpenGL ES 2.0 interface as Retra's custom shader feature. Off remains the default and fallback if a shader fails to compile.
