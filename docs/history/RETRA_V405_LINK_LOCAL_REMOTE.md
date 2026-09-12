# Retra v4.05 — Link Local / Link Remote menu flow

## Added
- `Link remote` now opens the requested four-choice panel:
  - Wi-Fi (server)
  - Wi-Fi (client)
  - Bluetooth (server)
  - Bluetooth (client)
- `Link local` now opens a `Load game` panel based on the supplied reference.
  - The currently running ROM is shown first.
  - `Another game…` opens Android's document picker for a GB/GBC/GBA ROM.
  - `Cancel` returns to Retra's main in-game menu.
- Back navigation returns from either link submenu to the main in-game menu.
- Because both are rendered inside the existing gameplay dialog, tapping outside the rounded menu frame still closes the menu and resumes gameplay.

## Backend status
The UI and selection flow are connected, but this project currently has only one embedded mGBA core instance. A second synchronized core is required for same-device local link play, and Retra does not yet include a phone-to-phone network/Bluetooth SIO transport. The app therefore does not claim a connection was made when those transports are selected.
