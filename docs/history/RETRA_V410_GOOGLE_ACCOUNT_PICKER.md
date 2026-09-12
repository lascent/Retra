# Retra v4.10 — Google Drive account picker

- Tapping **Sync to Google Drive** now opens Android's system account chooser first.
- The chooser is filtered to Google accounts already signed in on the phone.
- After choosing an account, Retra opens the Google Drive folder picker so the user can authorize a Retra saves folder in that account.
- The selected Google account is remembered and displayed in Settings.
- Sync settings now include **Change Google account**, **Change Drive folder**, **Sync now**, and **Disconnect**.
- Disconnect clears both the saved Drive folder and selected account.

This keeps Retra's existing Storage Access Framework folder sync while adding the requested signed-in Google account selection step.
