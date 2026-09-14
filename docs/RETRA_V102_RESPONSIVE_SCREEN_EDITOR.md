# Retra v1.0.2 — Responsive Screen Editor

This pass keeps the fast-forward performance work intact while improving the game-screen editor itself.

## Best Fit

The new `betterfit` mode uses the GBA 3:2 aspect ratio and, in landscape, targets approximately 68% of the editor width and the full usable height. This reproduces the proportions of the supplied 2048×921 reference while leaving balanced side areas for the controller overlay.

## Direct screen movement

The game screen can be dragged directly. Movement begins after a 3 px slop threshold so a normal tap still selects the screen and a hold still opens the size-mode menu. The frame remains clamped to the editor and persists as normalized coordinates, so it scales with the viewport instead of being tied to one phone resolution.

## Smoother gestures

Screen movement, edge resizing, and resize-handle scaling use the latest coalesced pointer sample and apply at most one geometry update per animation frame. During a gesture only the active screen/control is updated. Retra does not relayout every controller on every raw pointer event, and persistence/selection geometry is deferred until release.

## Resize handle

The screen resize handle now uses the same adaptive-corner logic as controller controls. The opposite corner stays anchored while scaling, making expand/minimize behavior predictable even near screen edges.
