# Notification Quick Reply

Status: planned, behind `NOTIFICATION_QUICK_REPLY`.

## What it is

Android notifications can carry a **direct reply** action. Tapping it opens a text field inside the
notification itself. When the person sends, Android delivers the text to the app through a
`RemoteInput`, without opening the app.

LogDate sends **capture nudges**: gentle reminders on the `ambient_capture_nudges` channel suggesting
the person write something down. Quick reply lets them answer the nudge with an entry right there.

## What the person sees

A capture nudge gains two actions:

- **Write.** This opens a reply field. After sending, the notification changes to a short
  confirmation, "Saved to your journal", and then dismisses.
- **Record.** This opens the editor already recording. It is the same as the Record voice shortcut.

If saving fails, the notification says what failed and offers Retry. The text is never dropped
silently.

Screenshots are added here when quick reply ships: the actions, the open reply field, the sending
state, the confirmation, and the failure state.

## How it works

1. `AmbientPromptNotificationCoordinator`
   (`app/compose-main/src/androidMain/kotlin/app/logdate/client/ambient/`) adds:
   - a Write action with a `RemoteInput` and a mutable `PendingIntent` to a reply receiver;
   - a Record action that sends the [capture launch contract](capture-launch-contract.md) intent in
     `Voice` mode.
2. The reply receiver reads the text with `RemoteInput.getResultsFromIntent`. It ignores blank input,
   saves a text entry through the existing entry-creation use case, and updates the notification.
3. The save runs with `goAsync()` so it finishes after `onReceive` returns.

## Flag and rollout

`NOTIFICATION_QUICK_REPLY`, off by default. With the flag off, nudges keep their current single tap
action.

## Privacy

A reply can be sent from the lock screen. The confirmation names only the action and never echoes the
entry text, so nothing private appears on the lock screen.

## How to test

- Receiver tests cover a successful save, blank input, and a failed save producing a visible error
  with Retry.
- An instrumented test posts a capture nudge through `AmbientPromptNotificationCoordinator` on a
  managed device and replies to it with `UiDevice`. Nudges are normally scheduled by
  `AmbientPromptScheduler`, so a test does not wait for the real schedule.

## Updating screenshots

The reply UI is system UI, so it is a Lane 2 capture. Record it with `./run screenshots:os`. It lands
in `docs/screenshots/android-os-integration/notification-quick-reply/`.
