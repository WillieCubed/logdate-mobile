# Wear OS dogfood checklist

Use paired emulators for repeatable transport and retained-data scenarios, and your own phone/watch
for actual microphone, speaker, Bluetooth, worn-state, battery, and background acceptance. Hardware
dogfood uses Play internal testing on both devices. Agent-driven physical-device commands are
forbidden; do not uninstall or clear retained LogDate data.

## Before you start

1. The Play Console steps in [Google Play Publishing](../reference/google-play-publishing.md#one-time-play-console-setup)
   are done and `LOGDATE_PLAY_WEAR_PUBLISH_ENABLED` is `true`.
2. A push to `main` has published both the phone build and the watch build. The watch build shows up
   under the Wear OS track in Play Console.
3. LogDate is installed on the phone from Play, and you are signed in.
4. LogDate is installed on the watch from the Play Store on the watch. Allow the microphone when asked.
5. The deliberately dispatched internal build defaults **Redesigned watch app** on for both
   phone and watch. If you explicitly turned it off in Labs, turn it back on there and reopen
   LogDate on the watch. Ordinary builds keep the redesign off by default until acceptance.
6. An older watch install under the retired `app.logdate.wear` package can stay while you test; it is a
   separate app and does not interfere. It will not update in place, and it never synced to the phone,
   so any recordings still on it exist nowhere else. Play them back or export them before you
   uninstall it.

Bring the phone and watch within range and confirm the watch's Settings screen says it is connected.

## Legacy presentation: recording

| Do this | You should see |
|---------|----------------|
| Tap the button, speak for 5 seconds, tap again | A stop haptic, then "Saved" with "Syncing to phone" and an Undo button |
| Press and hold, speak, let go | Recording only while held, then "Saved" |
| Tap and lift, then tap again after under half a second | "Too short", and nothing saved |
| Tap, pause, resume, stop | The timer stops while paused, and the saved length matches what you recorded |
| Tap, then tap Discard once | The timer stays and "Tap again to discard" replaces the waveform. The recording keeps going |
| Tap Discard, then tap the red bin within 3 seconds | Back to ready, nothing saved |
| Tap Discard, then wait 3 seconds | The question goes away and the recording is still running |
| Tap Discard, then tap Pause or Stop | The question goes away. Stop saves the recording |
| With a screen reader on, tap Discard once | The question is spoken |
| Save a recording, then Undo within five seconds | The note is gone from the watch, and from the phone a few seconds later |
| Record, then swipe back out of the app before stopping | The recording is saved, not lost, and appears in Voice memories |
| Record, then press the side button, then reopen LogDate | The recording continued while the app was in the background, and the screen still shows it recording |
| Record, press the side button, then tap Stop on the recording notification | The recording ends and is saved, and appears in Voice memories |
| Undo a recording with the phone out of range, then bring it back | The note never appears on the phone |
| Record 10 seconds, force-stop LogDate from the watch's settings, then open it again | The recording appears in Voice memories, about 10 seconds long |
| Record for 5 minutes with the screen off | A full-length recording, with sound throughout |
| Keep a recording going past 29 minutes | A haptic warning, then a save at 30 minutes |

## Interruptions

| Do this | You should see |
|---------|----------------|
| Start a recording, then have someone call the phone or play audio on the watch | "Paused by another app". Resuming continues the same recording |
| Turn microphone permission off in the watch's settings, then press the button | "Microphone is off" with an Allow button |

## Phone and sync

| Do this | You should see |
|---------|----------------|
| Record on the watch with the phone nearby | The note appears in the phone's timeline with playable audio within a minute, and the phone backs it up |
| Turn on airplane mode on the phone, record on the watch, then turn it off | The recording arrives on the phone after the phone comes back in range, and plays |

## Voice memories

| Do this | You should see |
|---------|----------------|
| Open the headphones button | Every recording, newest first, including ones the phone made |
| Tap a memory | It starts playing at once, with the time in the title |
| Pause, resume, skip back and forward 10 seconds | The progress bar follows |
| Play with no headphones connected on a watch with no speaker | "Connect headphones to listen", and the button opens Bluetooth settings |
| Play with the phone out of range a memory the watch never received | "Couldn't load audio. Tap to retry" |

## Mood

| Do this | You should see |
|---------|----------------|
| Check in "Sad" from the mood button, wait up to 15 minutes, then look at the Mood complication | 😔, the face the check-in screen showed |
| Check in "Great", wait up to 15 minutes, then open the Today's Summary tile | "😄 Great" |
| Check in a mood, then tap Open recorder | The recorder, and Timeline lists the mood for today |
| Open the check-in from the Mood complication, pick a mood, then tap Open recorder | The recorder, not the check-in again |

## Legacy presentation: reminders

| Do this | You should see |
|---------|----------------|
| Install this build over one that showed "Good morning!" or "Time to reflect", then wear the watch past 8 AM and past 9 PM | Neither notification appears |
| Open Settings on the watch | No Morning prompt or Evening prompt switch |
| Open LogDate's notification settings on the watch | No "Journal reminders" category |

## Legacy presentation: known gaps

These are known and should not be reported again as new bugs:

- The tile's Mic and Rec buttons both open the home recorder.
- Activity-aware location in Settings does not run on the watch.


## Companion redesign candidate and rollout

This is the existing release checklist for the Wear/phone redesign, not a general-availability
certification. Applicable Hypertext product standards are draft v0.11 (2026-10-11); source adoption
status remains unchanged. This material internal release affects Android phone and Wear OS 5+
recording, private content, device linking/revocation, cloud sync, and reminders. It does not introduce
payments or public sharing. Existing identity/export/recovery obligations remain in the product's
normal release work; this checklist does not silently certify them.

`WATCH_EXPERIENCE_V2` remains default OFF until the required paired and hardware outcomes below pass.
Flag-off compatibility must also remain usable. A local test or screenshot cannot substitute for
paired delivery, cloud persistence, provider revocation, or actual audio/device behavior.

The previous internal Play baseline is source `70ae657cac52a38a2d923047bda6ca910fa01365`:
phone version code 159 on `internal` and Wear version code 160 on `wear:internal`, recorded by
[the successful publish run](https://github.com/WillieCubed/logdate-mobile/actions/runs/38345721236).
This is a rollback reference, not the redesign candidate or proof of installation on a device.

Record once for the final integrated candidate:

| Release evidence | Result |
| --- | --- |
| Immutable integrated source SHA | Pending final verified batch |
| Phone internal artifact/version and Play receipt | Pending consolidated publication |
| Wear `wear:internal` artifact/version and Play receipt | Pending consolidated publication |
| Deployed backend release/revision and journey receipts | New atomic-write fix pending deployment; existing hosted protocol evidence is linked below |
| Paired emulator phone/watch identities and actual diagnostics | Pending verified retained pair; an AVD name is insufficient |
| Populated visual/accessibility matrix and hosted screenshot gate | Pending integrated render/CI result |
| User-operated internal hardware dogfood | Pending candidate installation and outcomes |
| Rollout enablement and legacy retirement | Pending all preceding acceptance |

Keep phone and Wear publication on the internal tracks. Do not promote mobile production, widen
availability, repeatedly upload each local commit, or update screenshot references merely to make a
failed comparison green. The root release operation must bind provider receipts to the same immutable
batch SHA and matching signing identity.

## Redesign acceptance scenarios

Run these on the final candidate with retained, disposable test content and a verified phone/watch
link. Record expected outcome, actual result, device/source revision, and recovery path. Run secure
removal last, because successful erasure intentionally removes the established link.

| Area | Complete journey and expected outcome | Required evidence / result |
| --- | --- | --- |
| Watch page, setup, Devices | With an absent, installed, incompatible, disconnected, and verified watch, open Watch/Devices and optional onboarding. Installation and repairs target the selected watch. Not now durably advances; retry preserves context. | Actual navigation/permission UI plus verified diagnostics; pending paired acceptance |
| Connection diagnostics | Inspect ordered seen/installed/protocol/association/link/ping/pending-sync/notifications checks. Revoke permission or disconnect and rerun. A failed or unknown check remains honest and its repair works. | Real nonce-bound watch RPC and permission observation; pending |
| Marked moments | Mark on the watch; find it on the phone timeline/day/place path, add a caption and accessible photo, open editor context, save, and reconcile to watch/cloud. Denied/limited photo access has an intentional state and granting access refreshes suggestions. | Actual source IDs, encrypted cloud receipt, resolved editor context, retained attachment; pending |
| Recording continuity | Start/hold/pause/resume on watch; see live phone chip; Stop from phone ends the actual recording and preserves it. Open goes to the relevant content. Interrupt/reconnect and retry without duplicate captures. | Paired messages and saved playable recording; pending |
| Playback continuity | Play on phone and Continue on watch at a nonzero position. Only an acknowledged receiver success completes handoff; unavailable output, missing content, or peer loss stays actionable and preserves source playback. | Actual receiver playback/position and failure UI; pending |
| Safe removal | Drain pending captures, remove watch, verify session revoke, disassociation, local erasure, and retained phone captures. Repeat offline/deferred, interrupted erase, and restart. Sign-out cannot skip a failed removal. | No remaining private database/media/preferences/in-memory keys/peer state, denied revoked token and later retry; pending |
| Content inputs | Change actual starred sources and previous completed week's persisted Rewind, publish the bounded window, and view it offline on watch. Toggle On this day/Rewind preferences. Local watch captures survive eviction. | Real membership/reasons, bounded counts/bytes, source preference propagation; pending |
| Reminders | Explicitly opt in, deny/grant/revoke notifications, change waking hours and shared choice, trigger supported signals, dismiss/fallback while phone/watch connectivity changes. Permission alone never opts in; cap and one-device arbitration hold. | Real device claim/post/dismiss and retained learned-hour adoption; pending |
| Media transport | Request uncached media, interrupt at a nonzero offset, reconnect and resume; play retained media offline. Exhaust storage/capacity safely and retry. | Range/offset and actual playable result, bounded cache, useful failure; pending |
| Account isolation | Switch/sign out/revoke while work is pending; old peer/account cannot read or append private content or repopulate a wiped watch. Explicit setup can relink only after erase completes. | Paired old/new account denial and retained accepted captures; pending |
| Cloud durability | Upload encrypted capture/mark through linked watch, read unchanged on phone, resolve/version-conflict/delete, restart/retry, and revoke. Inject failure between repo write and sync index on disposable PostgreSQL. | [Cloud acceptance](wear-cloud-companion-acceptance.md); deployed new revision still pending |
| Visual and input | Inspect populated compact/landscape/tablet/book/tabletop phone and small/large round watch states; combine dark/dynamic colors, RTL, 200% text, reduced motion, keyboard/screen reader/rotary and back/gesture input. | Actual render sheet plus runtime transitions/actions; pending integrated acceptance |

Phone Health Connect workout collection is explicitly omitted for this internal release, with
truthful settings copy and no exercise permission expansion. The compatible watch's passive trigger
remains conditional on activity permission and real device support. See
[workout release decision](wear-workout-reminder-release-decision.md).

## Failure and release recovery

- Preserve the previous internal build and backend revision identity. A failed candidate check must
  not activate the redesign or widen mobile tracks.
- Provider-backed backend smoke uses a disposable passkey account and private 0600 state. Candidate
  failure uses the existing cleanup/recovery flow; never borrow a retained user's credentials or data.
- Distinguish local saves, phone delivery, cloud backup, and completed removal in UI and evidence.
  Preserve pending work on failure and retry through the product rather than clearing data.
- Known unauthorized access, accepted-work loss/corruption, or unusable essential recovery blocks
  exposure of the affected capability. Missing paired/hardware evidence remains unverified; an agent
  must not approve its own exception.
- Record a final outcome for every row above before flag enablement and legacy cleanup. Successful
  publication alone completes distribution, not the redesign's acceptance.
