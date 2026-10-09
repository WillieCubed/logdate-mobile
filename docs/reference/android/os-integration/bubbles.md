# Bubbles

Status: planned. App Bubble readiness has no flag; the recording bubble is behind `RECORDING_BUBBLE`
and waits on a spike.

## What it is

A bubble is an app window that floats above other apps. It can be collapsed to an icon at the screen
edge and expanded into a small window. Android has two kinds:

| Kind | Who starts it | What it shows | Android version |
| --- | --- | --- | --- |
| **App Bubble** | The person, by long-pressing the app's launcher icon and choosing Bubble | The whole app, in a small floating window | Android 17 |
| **Notification bubble** | The app, by attaching `BubbleMetadata` to a notification | One activity the app chooses | Android 11+ |

On large screens, Android 17 also adds a **Bubble Bar** to the taskbar, where bubbles collect and can
be switched between.

LogDate uses both kinds:

- **App Bubble readiness.** Because people can bubble any app, Home and the editor must work in a
  bubble-sized window, and resizing must not restart them.
- **The recording bubble.** A floating recorder keeps a voice entry recording, with pause, stop, and
  save, while the person uses another app.

## What the person sees

- **Bubbling LogDate:** the timeline and the editor fit in the bubble. Nothing important is clipped,
  and controls stay reachable.
- **Floating a recording:** while recording, a "Float" action appears on the recording notification
  and in the editor's voice block. Tapping it collapses the recorder into a bubble, which expands into
  a compact recorder showing the waveform, elapsed time, Pause or Resume, Stop, and Save.

Screenshots are added here as each part ships: bubble-size Home and editor scenes, LogDate bubbled
from the launcher, the tablet Bubble Bar, and the recorder bubble collapsed and expanded.

## How it works

### App Bubble readiness

- `configChanges` on `MainActivity` and `EditorActivity` covers screen size, smallest screen size,
  screen layout, orientation, and density. Resizing a bubble then recomposes the UI instead of
  recreating the activity, so scroll position and in-progress text survive.
- Layouts are checked at bubble sizes, about 360×480dp and 320×400dp. The `Bubble` configuration in
  `@OsIntegrationPreviewMatrix` renders every OS-integration scene at that size.

### The recording bubble

A notification bubble must belong to a **conversation**: a notification with `MessagingStyle`, linked
to a long-lived sharing shortcut with a `Person`. LogDate's recorder is not a conversation, so the
design has to work within that rule:

1. **A long-lived shortcut.** LogDate publishes a long-lived "Voice recorder" shortcut whose `Person`
   is the user.
2. **A separate bubble notification.** "Float" posts a second notification with:
   - `MessagingStyle`, linked to that shortcut;
   - `BubbleMetadata` pointing at `RecordingBubbleActivity`;
   - `setAutoExpandBubble(true)`, so it opens right away;
   - `setSuppressNotification(true)`, so no duplicate shade entry appears.
3. **The recorder activity.** `RecordingBubbleActivity` is declared with
   `android:allowEmbedded="true"`, `android:resizeableActivity="true"`, and
   `android:documentLaunchMode="always"`, which bubbles require. It shows a compact recorder bound to
   the same `RecordingServiceController` the editor uses.

The recording keeps its own foreground-service notification. That notification is a
[Live Update](live-updates.md), and a Live Update cannot use `MessagingStyle`, which is why the bubble
needs a notification of its own.

Stopping or saving dismisses the bubble. Closing the bubble never stops or discards a recording.

### Spike result

Pending. The spike checks whether Android shows this notification as a bubble on API 30 and API 37.
Its decision is one of: ship as designed, ship only where `areBubblesAllowed()` is true, or drop the
recording bubble in favor of App Bubbles.

## Flag and rollout

- App Bubble readiness: no flag.
- Recording bubble: `RECORDING_BUBBLE`, off by default. `RecordingBubbleActivity` is declared disabled
  and enabled by `PlatformComponentToggler`.

## Privacy

The bubble shows recording time and the waveform, never transcripts or entry text.

## How to test

- Tests cover building the bubble notification, mapping the recorder state, and cleanup on stop,
  save, and process death.
- On an API 37 emulator, long-press the LogDate icon and choose Bubble.

## Updating screenshots

- The recorder UI and bubble-size scenes are Lane 1 references.
- The real bubbles and the Bubble Bar are Lane 2 captures, recorded with `./run screenshots:os`.
  They land in `docs/screenshots/android-os-integration/bubbles/`.
