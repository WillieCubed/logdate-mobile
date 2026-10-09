# Live Updates

Status: planned, behind `LIVE_UPDATES`.

## What it is

A **Live Update** is an ongoing notification that Android promotes to more visible places:

- a chip in the status bar;
- the top of the notification shade;
- the lock screen and always-on display.

Android 16 introduced Live Updates for activities the person started and is actively waiting on, such
as a ride, a delivery, or a timer. A voice recording, an export, a restore, and a sync all fit.

The app does not draw a Live Update; it asks for one. A notification is eligible for promotion when:

- it is ongoing, has a title, and requests promotion (`setRequestPromotedOngoing(true)`);
- it uses the standard, `BigTextStyle`, `CallStyle`, or `ProgressStyle` style, with no custom views
  and no colorization;
- the app declares `android.permission.POST_PROMOTED_NOTIFICATIONS`.

The person can turn promotion off per app in system settings. The notification then shows normally.

## What the person sees

| Work | Status bar chip | Expanded notification |
| --- | --- | --- |
| Voice recording | A microphone icon and the elapsed time | Elapsed time, with Pause and Stop |
| Export | Percent complete | Segmented progress through the export phases |
| Restore | Percent complete | Segmented progress, then completion or a specific failure |
| Sync | Shown only while a long sync runs | Progress, then completion or a specific failure |

On Android 17, failures use the system's semantic red and completion uses semantic green, so they
read the same as other apps' Live Updates.

Screenshots are added here when Live Updates ship: the chip, the expanded notification, and the lock
screen, on API 36 and API 37.

## How it works

- **Recording.** `AndroidAudioNotificationHandler`
  (`client/media/src/androidMain/kotlin/app/logdate/client/media/audio/`) builds the recording
  notification. With the flag on it adds:
  - `setRequestPromotedOngoing(true)`;
  - `setUsesChronometer(true)` with the recording start time;
  - `setShortCriticalText(...)`, the short text in the status bar chip.
- **Progress.** The export, restore, and cloud-sync notifications use
  `NotificationCompat.ProgressStyle`. Each phase becomes a segment, so the bar shows where the work
  is, not just how much is done.
- **Semantic colors (API 37).** `Notification.createSemanticStyleAnnotation` marks failure (red) and
  completion (green). Earlier versions get the plain style.
- **Library.** `androidx.core` 1.18 (already in the version catalog) provides the compat builders.

The notification channels are defined in `client/notifications/.../LogDateNotificationChannels.kt`.

## Flag and rollout

`LIVE_UPDATES`, off by default. With the flag off, every notification is built exactly as before.

## Privacy

The status bar chip and the lock-screen view show only time and progress, never entry text.

## How to test

- Host tests compare the builder output with the flag on and off: the promotion request, the chip
  text, the actions, and the segments.
- On an API 36 or 37 emulator, start a recording and look for the chip.

## Updating screenshots

Live Updates are system UI, so they are Lane 2 captures. Demo mode fixes the clock, so the chip text
is stable. Record them with `./run screenshots:os`. They land in
`docs/screenshots/android-os-integration/live-updates/`.
