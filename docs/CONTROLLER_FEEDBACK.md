# Controller Feedback

Retra's on-screen controller uses a single light feedback profile designed for rapid gameplay input.

## Haptics

- Uses `HapticFeedbackConstants.SEGMENT_FREQUENT_TICK` on Android 14+ and `CLOCK_TICK` on older supported Android versions.
- Uses Android's system-tuned haptic implementation instead of raw `Vibrator` pulse durations/amplitudes.
- Respects the device's global haptic-feedback setting.
- Uses a short global rate limit so D-pad rolling and multi-touch chords do not become continuous buzzing.

## Sound

- Uses Android's standard key-click sound at low volume.
- Uses a separate short rate limit from haptics so overlapping touches do not stack into a loud click.

## Input ordering

Gameplay input is applied before optional feedback. Haptics or sound can therefore never delay a key transition reaching the emulator core.

## User settings

`Settings -> Sound` exposes independent toggles for:

- Controller sound
- Controller haptics

Both default to enabled and are stored as portable Retra settings.
