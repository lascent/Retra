# Retra v1.0.0 — Persistent App Folder Fix

- The user selects the Retra app folder once with Android's system folder picker.
- Retra persists durable SAF write permission and the tree URI.
- On later launches, the remembered folder is reused without requiring **Use this folder** again.
- Save states, auto states, committed battery saves, portable metadata and settings snapshots are automatically mirrored to the selected folder.
- Rapid changes are coalesced on Retra's serialized storage executor to avoid repeated heavy folder exports.
- If Android/provider access is genuinely revoked, Retra clears the stale selection and asks for the folder again.
- `Metadata/settings.json` stores a portable user-facing settings snapshot while provider/account authorization remains device-local.
