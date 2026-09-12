# Retra v4.42 — Cover artwork and Settings bottom-safety fixes

- Restores `applyCoverToRenderedRom`, the shared UI renderer used after manual or automatic cover persistence.
- Adds a manual-cover display fallback when the newly written appassets URL is not immediately observable by WebView.
- Keeps IndexedDB as the compatibility fallback instead of treating native persistence failure as immediate data loss.
- Increases subpage scroll padding and adds a Settings-page scroll tail above Android gesture/3-button navigation.
