# Activity awareness on the phone and the watch

Status: design agreed, not yet built. Everything here ships behind a new `ACTIVITY_AWARENESS` flag.

## Why this exists

LogDate's moment reminders once had a trigger for "a workout ended". That trigger read the watch's
Health Services exercise state and nothing else, so it only ever fired after a logged workout. That
was too literal. What matters is noticing what someone is doing, so the app can react while it
happens and offer something useful when it ends:

- Nothing interrupts you while you are walking, riding or in a car. A reminder that comes due then
  waits until you stop.
- While you are on the move, the quickest way to capture comes first, and on the watch that is voice.
- When an activity ends, the app offers to add a moment from it.
- When you wake up, the day starts with a short prompt instead of an empty screen.

This design replaces the workout trigger with that broader idea, and keeps it as small as it can be
until there is real data about which suggestions people find useful.

## What the person sees

| Activity | While it happens | When it ends |
| --- | --- | --- |
| Moving (walking, running, cycling, or any exercise the watch records) | Reminders wait. Voice capture comes first on the watch's Today screen. | Today and Home offer "Add a moment from the last 40 minutes". This can also be the day's one reminder. |
| In a vehicle | Reminders wait. No prompts appear. | Today and Home offer "Add a moment from your 25-minute trip". This can also be the day's one reminder. |
| Asleep (watch only) | Nothing changes. | Today and Home show a morning start card with a voice capture shortcut. |
| Settled somewhere new after traveling | Nothing changes. | The existing "Arrived somewhere new" reminder, unchanged. |

The duration in each suggestion comes from the detected activity. One suggestion appears per activity.
It disappears when the person adds a moment, dismisses it, starts another activity, or two hours pass.

The reminder rules stay the same: reminders are off until the person turns them on, at most one
comes each day, it arrives on the device being worn, and it never comes outside the waking window.

## Decisions

These were settled while designing this feature:

- **Scope:** reminders first, then activity context on captured moments, then the app adapting to the
  activity. Each step is useful by itself, so they ship in that order.
- **Activities:** the end of a walk, run or ride; the end of a drive or commute; waking up; and
  settling somewhere after traveling.
- **Reactions depend on the activity.** "Wait until you stop", "voice first", the suggestion
  afterwards and the morning start are chosen per activity, as in the table above.
- **Activity context is stored with the moment, encrypted,** the same way `capturedOn` already is.
- **No decision model yet.** The rules decide. The app only records which suggestions people act on
  or dismiss, so a model has something to learn from later (see [Later: a decision model](#later-a-decision-model)).
- **One reminder a day still holds.** Activity triggers compete with the existing ones for that slot.

## Design

### Three activity kinds

```kotlin
// client/domain/.../activity/Activity.kt (commonMain)
enum class ActivityKind { MOVING, IN_VEHICLE, SLEEP }

data class Activity(
    val kind: ActivityKind,
    val startedAt: Instant,
    val endedAt: Instant?, // null while it is still going on
)
```

Walking, running and cycling get the same reactions, so they share one kind, `MOVING`. Settling after
travel is already handled by `NovelPlaceArrivalDetector` and needs no new kind.

The vehicle kind is called `IN_VEHICLE` rather than "driving" because detection cannot tell a driver
from a passenger. Holding reminders is right for both.

### One tracker per device

Each device runs one `ActivityTracker`. It turns the device's raw transitions into activities that
start, continue and end:

```kotlin
// client/domain/.../activity/ActivityTracker.kt (commonMain)
class ActivityTracker(
    private val transitions: Flow<ActivityTransition>, // this device's raw ENTER/EXIT signals
    private val peer: () -> Activity?,                 // the other device's current activity
    private val wornWatch: () -> Boolean,              // from the existing presence state
    private val store: KeyValueStorage,                // the open activity and the last ended one
    private val clock: Clock = Clock.System,
    scope: CoroutineScope,
) {
    val current: StateFlow<Activity?>
    val ended: SharedFlow<Activity>
}

data class ActivityTransition(val kind: ActivityKind, val entered: Boolean, val at: Instant)
```

Two constants keep detection steady, so a red light doesn't end a drive and a few steps don't count
as a walk:

| Kind | Must last at least | Ends after this long without the activity |
| --- | --- | --- |
| `MOVING` | 10 minutes | 3 minutes |
| `IN_VEHICLE` | 8 minutes | 4 minutes |
| `SLEEP` | 3 hours | 20 minutes |

An activity shorter than its minimum never produces a suggestion or a reminder. The open activity is
stored, so it survives the app process being stopped.

**Where the transitions come from.** Both sources exist already:

- **Phone:** Play services Activity Recognition transitions (walking, running, on a bicycle, in a
  vehicle, still). `ActivityAwareLocationService` already registers for them, but privately. Android
  allows one transition request per pending intent, so the registration moves into a shared
  `ActivityTransitionFeed` in `client/location`, which the location tracker and the activity tracker
  both read.
- **Watch:** Health Services passive user activity states. `WorkoutEndListenerService` becomes
  `UserActivityListenerService`: exercise maps to `MOVING` and asleep maps to `SLEEP`. It keeps the
  existing capability check and its restore at boot and app start. The watch cannot detect vehicles.

### Agreeing across the phone and the watch

Each device publishes its own current activity in the peer state document it already sends:
`PhoneState` and `WatchState` each gain one nullable `activity` field, a few dozen bytes. Each tracker
then picks the activity both devices act on by ranking what either one sees:

1. `IN_VEHICLE` from the phone, whenever the phone reports it.
2. `SLEEP` from the watch, while the watch is worn.
3. `MOVING` from either device.

This order exists because each device sees different things. The phone knows vehicles best, the watch
alone knows sleep, and the watch only reports exercise, not an ordinary walk, so a walk the phone sees
must still count. A device whose peer is out of reach uses only its own activity.

### Reactions

The policy is a plain function, tested case by case:

```kotlin
// client/domain/.../activity/ActivityReactions.kt (commonMain)
data class Reactions(
    val holdReminders: Boolean = false,
    val voiceFirst: Boolean = false,
    val suggestAfter: Boolean = false,
    val morningStart: Boolean = false,
)

fun reactionsFor(activity: Activity): Reactions =
    when (activity.kind) {
        ActivityKind.MOVING -> if (activity.endedAt == null) Reactions(holdReminders = true, voiceFirst = true) else Reactions(suggestAfter = true)
        ActivityKind.IN_VEHICLE -> if (activity.endedAt == null) Reactions(holdReminders = true) else Reactions(suggestAfter = true)
        ActivityKind.SLEEP -> if (activity.endedAt == null) Reactions() else Reactions(morningStart = true)
    }
```

### Reminders

- `ReminderSignals.workoutEndedAt` becomes `activity: ReminderActivity`, which holds the current
  activity and the last ended one. `LivePhoneReminderSnapshotSource` and the watch's dispatcher take
  `activity: suspend () -> ReminderActivity` in its place.
- `ReminderOrchestrator.decide` gains a `Defer(until)` outcome. When the current activity holds
  reminders, a reminder that comes due waits until the activity ends. It still has to fit the waking
  window and the one-a-day limit when it goes out.
- `ReminderTrigger` gains `ACTIVITY_ENDED`, which takes the place of `WORKOUT_ENDED` in the trigger
  order. The trigger travels between phone and watch, and an older peer cannot decode a new enum
  value. A device therefore sends `ACTIVITY_ENDED` only to a peer that advertises
  `ACTIVITY_REMINDERS` in `WearFeatures`. To other peers it sends `WORKOUT_ENDED`, and it reads
  `WORKOUT_ENDED` from them as a `MOVING` activity ending. `WORKOUT_ENDED` stays in the enum for that
  reason.
- Reminder copy uses the activity's duration, as in the table above, not its type. `MOVING` does not
  record whether it was a walk, a run or a ride, so the copy cannot say which.

### Activity context on captured moments

Captured notes and marked moments record what the person was doing:

```kotlin
val capturedDuring: ActivityKind? = null // next to capturedOn
```

- **Stamping:** `AudioMomentSaver`, `QuickNoteWriter`, `MarkMomentUseCase` and the phone editor read
  `ActivityTracker.current` when they save. Nothing is stamped while the flag is off.
- **Storage:** a nullable `captured_during` column on the note entities and on marked moments. This
  needs a Room migration; use the next free database version when it is built, because other work in
  flight also claims the next version.
- **Sync:** encrypted in the sync payload with the same helpers as `capturedOn`. A value the reader
  does not recognize decodes as null, so a newer kind never breaks an older device.
- **Display:** the timeline and Rewind show "While moving" or "In a vehicle" next to where the moment
  was captured. Sleep is never shown.

### What the app shows

`ActivityPresenter` turns the agreed activity and its reactions into UI state, so the phone and the
watch show the same thing:

```kotlin
class ActivityPresenter(
    private val tracker: ActivityTracker,
    private val outcomes: SuggestionOutcomes,
    private val clock: Clock = Clock.System,
    scope: CoroutineScope,
) {
    val state: StateFlow<ActivityUiState> // voice first, the suggestion card, or the morning start
}
```

`TodayViewModel` on the watch and the Home card on the phone take an `ActivityPresenter` as their only
new dependency. Acting on a suggestion opens capture with the activity's start time as the moment's
time, and the activity already stamped.

### Recording what people do with suggestions

```kotlin
class SuggestionOutcomes(private val store: KeyValueStorage) {
    suspend fun shown(suggestion: SuggestionKind, activity: Activity)
    suspend fun actedOn(suggestion: SuggestionKind, activity: Activity)
    suspend fun dismissed(suggestion: SuggestionKind, activity: Activity)
}
```

It stays on the device and is never synced. It keeps 30 days of outcomes. It also tells the presenter
whether a suggestion was already shown for an activity, which enforces one suggestion per activity.

## Permissions and the flag

- **Flag:** `ACTIVITY_AWARENESS`, off by default, offered in Labs with plain wording that explains
  what the app notices and that nothing leaves the device except the encrypted context on moments.
- **Permission:** `ACTIVITY_RECOGNITION` on both devices. The watch already holds it for the workout
  trigger. The phone reuses the location tracker's grant when it has one, and otherwise asks when the
  person turns the flag on. Without the permission the feature stays off and the Labs row says why.
- **Play listing:** the permission already appears for the watch. Nothing new is added to the phone's
  listing if the location tracker's declaration already covers it; check before release.

## Testing

- **Tracker:** unit tests with fake transitions and a test clock for the minimum durations, the end
  grace periods, the cross-device ranking, a peer out of reach, and restoring the open activity after a
  restart.
- **Reactions:** one test per kind and phase.
- **Reminders:** a reminder that comes due during an activity is deferred and goes out after it ends,
  but not outside the waking window and not as a second reminder that day. Old and new peers exchange
  `WORKOUT_ENDED` and `ACTIVITY_ENDED` correctly.
- **Capture context:** stamping, the Room migration test, and sync round trips including an unknown
  kind.
- **UI:** the watch Today suggestion and morning start on the Wear emulator; the phone Home card in the
  shared screenshot catalog at the sizes the settings and workspace contracts require.

## Build order

1. `ActivityTransitionFeed`, the generalized watch listener, `ActivityTracker` and the peer state
   field. With these, reminders defer during activities and `ACTIVITY_ENDED` replaces the workout
   trigger.
2. `capturedDuring`: stamping, storage, sync and display.
3. `ActivityPresenter` and `SuggestionOutcomes`: voice first, the suggestion card and the morning
   start on both devices.

## Later: a decision model

A decision model such as Jev could later choose among the reactions the rules allow, using the
outcomes recorded above. It would sit behind the same `reactionsFor` call. Two questions stay open
until there is data to justify one:

- **Where it runs:** on the device, or through the LogDate server so no model key ships in the app and
  requests carry only derived features, never journal text or locations.
- **What it learns from:** whether the outcomes ever leave the device, even anonymized.

Guardrails stay in code either way: opt-in, one reminder a day, the waking window, the worn device,
and holding reminders while someone is in a vehicle.
