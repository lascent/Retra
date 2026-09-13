# Retra v1.0.1 — real-device release validation matrix

These cases cannot be proven by source/contract tests alone. Run them on signed release
builds using the exact mGBA revision in `third_party/mgba.lock` and record device model,
Android version, navigation mode, display refresh rate, build commit, pass/fail, and a
short evidence note/video/logcat reference.

| Area | Minimum device/setup | Required checks | Pass condition |
| --- | --- | --- | --- |
| Google Drive | Physical phone with Google Play Services + test Google account | authorize; initial upload; download to second clean install/device; modify both sides; conflict/backup case; disconnect/reconnect | no silent data loss; hashes match; conflict backup is retained; auth cancellation is safe |
| Wi-Fi Link | Two physical Android devices on same LAN | host/client connect; normal Link Cable gameplay; background/foreground; transient packet loss; disconnect/reconnect; desync recovery | connection state remains accurate; bounded recovery works; no corrupted battery save |
| Bluetooth Link | Two physical Android devices with Bluetooth | permission flow; pair/connect; gameplay; screen off/on; move briefly out of range; reconnect | permission UX is correct; transport recovers or exits cleanly; saves remain valid |
| IME / keyboard | Gesture-nav + 3-button-nav phones | open search/text fields; rotate with keyboard open; dismiss keyboard; bottom sheets/dialogs with text entry | focused field and actions stay visible; no double bottom inset; no stuck blank area after IME closes |
| Foldable / resize | Foldable or Android foldable emulator plus at least one physical large-screen device if available | folded/unfolded; portrait/landscape; split-screen resize during Library, Settings, modal, and gameplay return | no fixed phone frame, clipped actions, duplicated insets, or stale layout after bounds change |
| 60 Hz | Physical 60 Hz device/mode | 15+ min gameplay; menus; pause/resume; fast-forward on/off | stable ~59.73 FPS game cadence; no periodic pacing hitch; audio stays synchronized |
| 90 Hz | Physical 90/120 Hz device forced to 90 Hz if supported | menus and gameplay; inspect requested display mode/logcat | Retra does not create an avoidable uneven gameplay cadence when a cleaner supported mode is available; fallback remains stable |
| 120 Hz | Physical 120 Hz device | gameplay + UI; thermal/battery saver transitions | responsive UI and clean ~2:1 presentation cadence; emulator speed remains authentic; cap drops safely under constraints |

## Evidence to capture

For each run, save:

- signed APK/AAB version and Retra Git commit;
- mGBA SHA from `third_party/mgba.lock`;
- device model, SoC/RAM, Android version;
- navigation mode and display mode;
- `adb logcat` lines tagged `Retra.Display` and Remote Link/Drive errors when relevant;
- screenshots/video for inset, foldable and IME cases;
- before/after hashes for save/cloud conflict cases.

A release candidate should not be tagged until every row has at least one passing physical-device run; Wi-Fi and Bluetooth require two physical devices.
