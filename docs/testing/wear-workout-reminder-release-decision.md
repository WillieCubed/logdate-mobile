# Workout reminders in the internal companion release

Decision recorded 2026-10-12: omit **phone Health Connect workout collection** from
this internal release. Preserve the implemented Wear OS passive workout trigger.
This follows the omission path in the companion design's **Risks → Play review**
(`docs/superpowers/specs/2026-10-02-wear-companion-experience-design.md`): Health
Connect workout reads require a Play Console declaration; without that declaration,
the workout trigger does not ship through Health Connect. The master implementation
plan's R2 and Play permissions risk identify the same dependency.

## User-visible behavior

- Phone Reminders settings explicitly says phone workout reminders are not included
  in this version. It directs users to activity access on a compatible watch rather
  than offering a phone permission request that cannot enable the feature.
- The phone can still deliver opted-in reminders for new places, wind-down, campfire,
  drafts, and memories according to each signal's availability and the shared daily
  limit.
- A compatible watch retains its passive workout-end detection when the redesign,
  shared reminders choice, notification permission, and activity access allow it.
  Its reminder dispatcher still applies the 30-minute recency window, waking window,
  and daily authorization. This decision does not enable the redesign rollout flag.

## Implementation boundary

`PhoneReminderSignalsModule` does not supply a workout reader to
`LivePhoneReminderSnapshotSource`; the default reader returns null. The phone does
not read `ExerciseSessionRecord`, request `android.permission.health.READ_EXERCISE`,
or register workout polling. Existing sleep access does not imply consent to read
workouts. No new Play health declaration or permission expansion accompanies this
omission, and distribution remains internal-only.

`WorkoutEndListenerService`, `WatchReminders`, and `WatchReminderDispatcher` retain
the separate Wear OS passive signal. Watch capability and permission checks remain
authoritative; the phone settings explanation is not evidence of runtime availability
on any particular watch.

## Prerequisites for adding phone workout reminders later

1. Complete the Play Console Health Connect declaration for reading exercise sessions
   for contextual journaling reminders. Identify the precise permission and purpose,
   and supply the required privacy-policy and permission-flow demonstration evidence.
2. Implement a `HealthConnectWorkoutSessionSource` that checks provider availability
   and explicit exercise permission before reading, returning only the latest session
   that ended within the preceding 30 minutes. Revocation must stop reads immediately.
3. Wire the source into the existing consent-gated reminder snapshot and periodic
   worker, preserving account rechecks, shared daily limits, and device arbitration.
   Add an explicit, optional permission flow; enabling reminders or existing sleep
   access alone must never authorize workout reads.
4. Replace the phone unavailability explanation with real provider/permission
   availability and a working fix action. Verify granted, denied, revoked, unavailable,
   stale, future, overnight, and watch-worn delivery cases before internal publication.

These prerequisites are future work for adding the omitted phone signal, not claims
that it exists in this release. User-operated hardware dogfood and the broader paired
reminder acceptance remain separate release requirements.
