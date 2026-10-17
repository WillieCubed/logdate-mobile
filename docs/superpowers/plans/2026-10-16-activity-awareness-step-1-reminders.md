# Activity awareness, step 1: tracking and reminders — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The phone and the watch notice when someone is moving, in a vehicle or asleep. Reminders wait while an activity is under way, and the end of an activity can be the day's one reminder, replacing the workout trigger.

**Architecture:** A pure reducer in `client/domain` turns raw ENTER/EXIT transitions into activities with minimum durations and end grace periods. `ActivityTracker` persists it and agrees with the peer device through one new field in the peer state document. Each device feeds it from a source it already has: Play services Activity Recognition on the phone and Health Services passive states on the watch. `ReminderOrchestrator` reads the agreed activity.

**Tech Stack:** Kotlin Multiplatform (commonMain), kotlinx.serialization, kotlinx.coroutines, Koin, Play services `ActivityRecognitionClient`, Health Services `PassiveMonitoringClient`.

**Spec:** `docs/superpowers/specs/2026-10-16-activity-awareness-design.md`. Steps 2 (activity context on moments) and 3 (suggestions and morning start) get their own plans.

## Global Constraints

- Everything new is behind `FeatureFlag.ACTIVITY_AWARENESS` (key `activity_awareness_enabled`, default off). With the flag off, the watch's workout reminder works exactly as it does today.
- Activity kinds are exactly `MOVING`, `IN_VEHICLE`, `SLEEP`.
- Minimum durations: `MOVING` 10 min, `IN_VEHICLE` 8 min, `SLEEP` 3 h. End grace: `MOVING` 3 min, `IN_VEHICLE` 4 min, `SLEEP` 20 min.
- Agreement order: `IN_VEHICLE` from the phone, then `SLEEP` from the watch while worn, then `MOVING` from either.
- At most one reminder a day, only in the waking window, on the worn device. Activity triggers compete for that one slot.
- A trigger that an older peer cannot decode is never sent to it. Watches advertise `WearFeatures.ACTIVITY_REMINDERS = "reminders.activity.v1"`.
- Logging is Napier only. Time is `kotlin.time.Instant`. UI copy never says "we".
- Commit scopes: `reminders` for shared reminder behavior, `wear` for watch-only changes.

## Design changes found while planning

- **No shared transition feed.** Activity Recognition replaces a request only when it reuses the same `PendingIntent`. The tracker registers its own request with its own receiver, so `ActivityAwareLocationService` stays as it is.
- **Holding is a skip, not a deferral.** `ReminderOrchestrator.decide` returns `Skip(SkipReason.ACTIVITY_UNDER_WAY)` while the current activity holds reminders. Delivery already asks again whenever a signal changes, and the activity ending is such a change, so nothing needs to remember a deferred reminder.
- **Two triggers instead of one.** `ReminderTrigger` gains `MOVING_ENDED` and `TRIP_ENDED`, so the copy can name the activity without extra payload fields.

## File structure

| File | Responsibility |
| --- | --- |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/Activity.kt` | `ActivityKind`, `Activity`, `ActivityTransition`, the steadiness constants |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/ActivityLog.kt` | The pure reducer: open activity, transitions, settling, last ended |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/ActivityAgreement.kt` | Picking the activity both devices act on |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/ActivityTracker.kt` | Persistence, the settle timer, local and agreed state |
| `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt` | `ActivitySnapshot` on `PhoneState` and `WatchState` |
| `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/*` | Signals, orchestrator, triggers, delivery routing |
| `app/compose-main/src/androidMain/kotlin/app/logdate/client/activity/*` | Phone: transition registration and receiver |
| `app/wear/src/main/kotlin/app/logdate/wear/reminders/*` | Watch: Health Services states into the tracker, reminders |

---

### Task 1: The activity model and its reducer

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/Activity.kt`
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/ActivityLog.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/activity/ActivityLogTest.kt`

**Interfaces:**
- Produces: `enum class ActivityKind { MOVING, IN_VEHICLE, SLEEP }`; `data class Activity(kind, startedAt, endedAt: Instant?)`; `data class ActivityTransition(kind, entered: Boolean, at: Instant)`; `@Serializable data class ActivityLog(open: OpenActivity?, lastEnded: Activity?)` with `fun accept(transition): ActivityLog`, `fun settle(now): ActivityLog`, `val current: Activity?`, `fun settlesAt(): Instant?`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class ActivityLogTest {
    private val start = Instant.fromEpochSeconds(1_760_000_000)

    @Test
    fun `a walk long enough ends after its grace period`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, entered = true, at = start))
                .accept(ActivityTransition(ActivityKind.MOVING, entered = false, at = start + 40.minutes))
        assertEquals(Activity(ActivityKind.MOVING, start, endedAt = null), log.current)
        assertEquals(start + 43.minutes, log.settlesAt())

        val settled = log.settle(start + 43.minutes)
        assertNull(settled.current)
        assertEquals(Activity(ActivityKind.MOVING, start, start + 40.minutes), settled.lastEnded)
    }

    @Test
    fun `stopping briefly does not end a drive`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, true, start))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, false, start + 10.minutes))
                .settle(start + 12.minutes)
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, true, start + 12.minutes))
        assertEquals(Activity(ActivityKind.IN_VEHICLE, start, null), log.current)
        assertNull(log.settlesAt())
    }

    @Test
    fun `an activity shorter than its minimum is forgotten`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, true, start))
                .accept(ActivityTransition(ActivityKind.MOVING, false, start + 4.minutes))
                .settle(start + 7.minutes)
        assertNull(log.current)
        assertNull(log.lastEnded)
    }

    @Test
    fun `a new kind ends the open activity where it was left`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, true, start))
                .accept(ActivityTransition(ActivityKind.MOVING, false, start + 15.minutes))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, true, start + 16.minutes))
        assertEquals(Activity(ActivityKind.MOVING, start, start + 15.minutes), log.lastEnded)
        assertEquals(Activity(ActivityKind.IN_VEHICLE, start + 16.minutes, null), log.current)
    }

    @Test
    fun `an exit for another kind is ignored`() {
        val log =
            ActivityLog()
                .accept(ActivityTransition(ActivityKind.MOVING, true, start))
                .accept(ActivityTransition(ActivityKind.IN_VEHICLE, false, start + 5.minutes))
        assertNull(log.settlesAt())
    }
}
```

- [ ] **Step 2: Run them and confirm they fail to compile**

Run: `./gradlew :client:domain:desktopTest --tests '*ActivityLogTest*'`
Expected: FAIL, unresolved reference `ActivityLog`.

- [ ] **Step 3: Implement the model and reducer**

`Activity.kt`:

```kotlin
package app.logdate.client.domain.activity

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What someone is doing, as the phone and watch can tell. */
@Serializable
enum class ActivityKind(
    /** How long it has to last to count as an activity. */
    val minimum: Duration,
    /** How long it can stop before it counts as ended, so a red light doesn't end a drive. */
    val endGrace: Duration,
) {
    /** Walking, running, cycling, or any exercise the watch records. */
    MOVING(minimum = 10.minutes, endGrace = 3.minutes),

    /** In a car, bus or train; detection cannot tell a driver from a passenger. */
    IN_VEHICLE(minimum = 8.minutes, endGrace = 4.minutes),

    /** Asleep, which only the watch can tell. */
    SLEEP(minimum = 3.hours, endGrace = 20.minutes),
}

/** One activity; [endedAt] is null while it is still going on. */
@Serializable
data class Activity(
    val kind: ActivityKind,
    val startedAt: Instant,
    val endedAt: Instant?,
)

/** One raw signal from a device: someone started ([entered]) or stopped doing [kind] at [at]. */
data class ActivityTransition(
    val kind: ActivityKind,
    val entered: Boolean,
    val at: Instant,
)
```

`ActivityLog.kt`:

```kotlin
package app.logdate.client.domain.activity

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** The activity under way, and when its signal last stopped, if it did. */
@Serializable
data class OpenActivity(
    val kind: ActivityKind,
    val startedAt: Instant,
    val leftAt: Instant? = null,
)

/**
 * One device's activities: the one under way and the last one that ended. Pure, so every rule is
 * tested without a clock or storage.
 */
@Serializable
data class ActivityLog(
    val open: OpenActivity? = null,
    val lastEnded: Activity? = null,
) {
    /** The activity under way, counted from its first signal so reminders wait at once. */
    val current: Activity? get() = open?.let { Activity(it.kind, it.startedAt, endedAt = null) }

    fun accept(transition: ActivityTransition): ActivityLog {
        val open = open
        return when {
            transition.entered && open == null -> copy(open = OpenActivity(transition.kind, transition.at))
            transition.entered && open?.kind == transition.kind -> copy(open = open.copy(leftAt = null))
            transition.entered && open != null -> end(open, open.leftAt ?: transition.at).copy(open = OpenActivity(transition.kind, transition.at))
            open?.kind == transition.kind && open.leftAt == null -> copy(open = open.copy(leftAt = transition.at))
            else -> this
        }
    }

    /** When the open activity counts as ended, if its signal has stopped. */
    fun settlesAt(): Instant? = open?.leftAt?.let { it + open.kind.endGrace }

    /** Ends the open activity once its grace period has passed by [now]. */
    fun settle(now: Instant): ActivityLog {
        val open = open ?: return this
        val leftAt = open.leftAt ?: return this
        if (now < leftAt + open.kind.endGrace) return this
        return end(open, leftAt)
    }

    private fun end(
        open: OpenActivity,
        endedAt: Instant,
    ): ActivityLog {
        val lasted = endedAt - open.startedAt >= open.kind.minimum
        return ActivityLog(open = null, lastEnded = if (lasted) Activity(open.kind, open.startedAt, endedAt) else lastEnded)
    }
}
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew :client:domain:desktopTest --tests '*ActivityLogTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit** (with Task 2, as one change)

### Task 2: Agreeing across devices, and the peer state field

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/ActivityAgreement.kt`
- Modify: `client/sync/src/commonMain/kotlin/app/logdate/client/sync/peer/state/StateDocument.kt` (add `ActivitySnapshot`, and `activity: ActivitySnapshot? = null` on `PhoneState` and `WatchState`)
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/activity/ActivityAgreementTest.kt`, `client/sync/src/commonTest/.../StateDocumentTest.kt` (round trip and an unknown kind)

**Interfaces:**
- Consumes: `Activity`, `ActivityKind`, `ActivityLog` (Task 1).
- Produces: `fun agreedActivity(phone: Activity?, watch: Activity?, watchWorn: Boolean): Activity?`; `fun latestEnded(vararg ended: Activity?): Activity?`; `@Serializable data class ActivitySnapshot(kind: String, startedAt: Long, endedAt: Long? = null, lastEndedKind: String? = null, lastStartedAt: Long? = null, lastEndedAt: Long? = null)`; `fun ActivityLog.toSnapshot(): ActivitySnapshot?`; `fun ActivitySnapshot.toLog(): ActivityLog` (unknown kinds become null).

- [ ] **Step 1: Write the failing tests**

```kotlin
class ActivityAgreementTest {
    private val at = Instant.fromEpochSeconds(1_760_000_000)
    private val walk = Activity(ActivityKind.MOVING, at, null)
    private val drive = Activity(ActivityKind.IN_VEHICLE, at, null)
    private val sleep = Activity(ActivityKind.SLEEP, at, null)

    @Test
    fun `the phone decides vehicles`() = assertEquals(drive, agreedActivity(phone = drive, watch = walk, watchWorn = true))

    @Test
    fun `the worn watch decides sleep`() = assertEquals(sleep, agreedActivity(phone = walk, watch = sleep, watchWorn = true))

    @Test
    fun `sleep from a watch on the nightstand does not count`() = assertNull(agreedActivity(phone = null, watch = sleep, watchWorn = false))

    @Test
    fun `a walk the phone sees counts though the watch saw no exercise`() = assertEquals(walk, agreedActivity(phone = walk, watch = null, watchWorn = true))

    @Test
    fun `the later ending wins`() {
        val earlier = Activity(ActivityKind.MOVING, at, at + 20.minutes)
        val later = Activity(ActivityKind.IN_VEHICLE, at + 30.minutes, at + 50.minutes)
        assertEquals(later, latestEnded(earlier, later, null))
    }
}
```

- [ ] **Step 2: Run them and confirm they fail**

Run: `./gradlew :client:domain:desktopTest --tests '*ActivityAgreementTest*'`
Expected: FAIL, unresolved references.

- [ ] **Step 3: Implement**

```kotlin
package app.logdate.client.domain.activity

/**
 * The activity both devices act on. The phone knows vehicles best, only the worn watch knows sleep,
 * and the watch reports exercise but not an ordinary walk, so a walk either device sees counts.
 */
fun agreedActivity(
    phone: Activity?,
    watch: Activity?,
    watchWorn: Boolean,
): Activity? =
    phone?.takeIf { it.kind == ActivityKind.IN_VEHICLE }
        ?: watch?.takeIf { it.kind == ActivityKind.SLEEP && watchWorn }
        ?: listOfNotNull(phone, watch).firstOrNull { it.kind == ActivityKind.MOVING }

/** The most recently ended of [ended]. */
fun latestEnded(vararg ended: Activity?): Activity? = ended.filterNotNull().maxByOrNull { it.endedAt ?: it.startedAt }
```

In `StateDocument.kt`, next to `CampfireSnapshot`:

```kotlin
/** A device's activity log for its peer. Kinds are names, so a kind this build does not know reads as nothing. */
@Serializable
data class ActivitySnapshot(
    val kind: String? = null,
    val startedAt: Long? = null,
    val lastEndedKind: String? = null,
    val lastStartedAt: Long? = null,
    val lastEndedAt: Long? = null,
)
```

and add `val activity: ActivitySnapshot? = null,` as the last property of both `PhoneState` and `WatchState`. The conversions live in `client/domain/.../activity/ActivitySnapshots.kt`, because `client/sync` does not see domain types:

```kotlin
fun ActivityLog.toSnapshot(): ActivitySnapshot? =
    if (open == null && lastEnded == null) {
        null
    } else {
        ActivitySnapshot(
            kind = open?.kind?.name,
            startedAt = open?.startedAt?.toEpochMilliseconds(),
            lastEndedKind = lastEnded?.kind?.name,
            lastStartedAt = lastEnded?.startedAt?.toEpochMilliseconds(),
            lastEndedAt = lastEnded?.endedAt?.toEpochMilliseconds(),
        )
    }

/** The peer's current activity; its grace period is the peer's to judge, so it has no `leftAt`. */
fun ActivitySnapshot.current(): Activity? {
    val kind = kind?.let { name -> ActivityKind.entries.firstOrNull { it.name == name } } ?: return null
    return Activity(kind, Instant.fromEpochMilliseconds(startedAt ?: return null), endedAt = null)
}

fun ActivitySnapshot.lastEnded(): Activity? {
    val kind = lastEndedKind?.let { name -> ActivityKind.entries.firstOrNull { it.name == name } } ?: return null
    return Activity(kind, Instant.fromEpochMilliseconds(lastStartedAt ?: return null), Instant.fromEpochMilliseconds(lastEndedAt ?: return null))
}
```

- [ ] **Step 4: Run the domain and sync tests**

Run: `./gradlew :client:domain:desktopTest --tests '*Activity*' :client:sync:desktopTest --tests '*StateDocument*'`
Expected: PASS. Also run `:client:domain:compileKotlinIosSimulatorArm64` so `commonMain` still compiles for iOS.

- [ ] **Step 5: Commit**

`chore(reminders): Track activities and agree on them across devices` with Tasks 1 and 2.

### Task 3: The tracker, with persistence and a settle timer

**Files:**
- Create: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/activity/ActivityTracker.kt`
- Test: `client/domain/src/commonTest/kotlin/app/logdate/client/domain/activity/ActivityTrackerTest.kt`

**Interfaces:**
- Consumes: Tasks 1 and 2; `KeyValueStorage` (`getString`, `putString`).
- Produces:

```kotlin
class ActivityTracker(
    private val store: KeyValueStorage,
    private val peer: Flow<ActivitySnapshot?>,
    private val watchWorn: Flow<Boolean>,
    private val isPhone: Boolean,
    private val enabled: Flow<Boolean>,
    private val clock: Clock = Clock.System,
) {
    val local: StateFlow<ActivityLog>          // this device's log, for its peer state document
    val agreed: StateFlow<AgreedActivity>      // what both devices act on; empty while the flag is off
    suspend fun accept(transition: ActivityTransition)
    fun start(scope: CoroutineScope)           // restores the log, settles on a timer, follows the peer
}

data class AgreedActivity(val current: Activity? = null, val lastEnded: Activity? = null)
```

- [ ] **Step 1: Write the failing tests** with `runTest`, a `MutableStateFlow` peer, an in-memory `KeyValueStorage` fake (copy `InMemoryKeyValueStorage` from `client/datastore` tests if one exists; otherwise a `mutableMapOf<String, String>()` fake), and `TestTimeSource`-backed clock:
  - accepting ENTER then EXIT, then advancing virtual time past the grace period, moves the walk into `agreed.lastEnded`;
  - a new tracker on the same store restores the open activity;
  - with `enabled = false`, `agreed` stays empty while `local` still records;
  - the phone's tracker reports the watch's sleep when `watchWorn` is true, and not when false.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :client:domain:desktopTest --tests '*ActivityTrackerTest*'`
- [ ] **Step 3: Implement.** Persist `ActivityLog` as JSON under `activity.log` after every change. The settle timer: in `start`, collect `local` with `collectLatest`; when `settlesAt()` is not null, `delay(settlesAt - clock.now())`, then replace the log with `settle(clock.now())`. `agreed` is `combine(local, peer, watchWorn, enabled)` mapping to `AgreedActivity(agreedActivity(phone, watch, worn), latestEnded(...))`, where this device's log fills the `phone` or `watch` slot by `isPhone`.
- [ ] **Step 4: Run and confirm the tests pass.**
- [ ] **Step 5: Commit** with Task 4.

### Task 4: Reminders wait during an activity, and an ending can remind

**Files:**
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderModels.kt` (`ReminderSignals.activity`, `ReminderTrigger.MOVING_ENDED`/`TRIP_ENDED`, `SkipReason.ACTIVITY_UNDER_WAY`)
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/ReminderOrchestrator.kt`
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/LivePhoneReminderSnapshotSource.kt` (`activity: suspend () -> AgreedActivity` replaces `workoutEndedAt`)
- Modify: `client/domain/src/commonMain/kotlin/app/logdate/client/domain/reminders/PhoneReminderDelivery.kt` (`ReminderWatch.supportsActivityReminders`; an activity trigger goes to a watch only when it is true)
- Modify: `client/sync/.../datalayer/v2/WearFeatures` (add `ACTIVITY_REMINDERS`, included in `ALL`)
- Modify: the phone's and the watch's trigger copy: `PhoneMomentReminderNotifier`, `WatchReminder`
- Test: `ReminderOrchestratorTest`, `PhoneReminderDeliveryTest`, `LivePhoneReminderSnapshotSourceTest`

**Interfaces:**
- Consumes: `AgreedActivity` (Task 3).
- Produces: `ReminderSignals.activity: AgreedActivity = AgreedActivity()`; `ReminderOrchestrator.ACTIVITY_RECENCY = 30.minutes`.

- [ ] **Step 1: Write the failing orchestrator tests**

```kotlin
@Test
fun `nothing reminds while someone is in a vehicle`() {
    val signals = signals(writtenToday = false, activity = AgreedActivity(current = Activity(ActivityKind.IN_VEHICLE, now - 20.minutes, null)))
    assertEquals(ReminderDecision.Skip(SkipReason.ACTIVITY_UNDER_WAY), ReminderOrchestrator.decide(eveningClock, signals))
}

@Test
fun `a walk that just ended can be the day's reminder`() {
    val walk = Activity(ActivityKind.MOVING, now - 50.minutes, now - 10.minutes)
    val decision = ReminderOrchestrator.decide(clock, signals(activity = AgreedActivity(lastEnded = walk)))
    assertEquals(ReminderDecision.Deliver(ReminderTrigger.MOVING_ENDED, openMarks = 0), decision)
}

@Test
fun `a trip that ended an hour ago no longer reminds`() {
    val trip = Activity(ActivityKind.IN_VEHICLE, now - 90.minutes, now - 60.minutes)
    assertEquals(ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND), ReminderOrchestrator.decide(clock, signals(activity = AgreedActivity(lastEnded = trip))))
}

@Test
fun `waking up is not a reminder`() {
    val night = Activity(ActivityKind.SLEEP, now - 8.hours, now - 5.minutes)
    assertEquals(ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND), ReminderOrchestrator.decide(clock, signals(activity = AgreedActivity(lastEnded = night))))
}
```

Add delivery tests: `MOVING_ENDED` goes to a reachable watch with `supportsActivityReminders = true`, and posts on the phone when it is false.

- [ ] **Step 2: Run and confirm failure.** `./gradlew :client:domain:desktopTest --tests '*Reminder*'`
- [ ] **Step 3: Implement.** In `decide`, after the waking-window and already-reminded checks: `if (signals.activity.current?.kind?.holdsReminders == true) return Skip(ACTIVITY_UNDER_WAY)`, where `holdsReminders` is true for `MOVING` and `IN_VEHICLE`. In `firstTrigger`, before the arrival check: an `activity.lastEnded` of kind `MOVING` or `IN_VEHICLE` that ended within `ACTIVITY_RECENCY` returns `MOVING_ENDED` or `TRIP_ENDED`. Keep the `workoutEndedAt` check for the watch's old path until Task 6 removes its last reader. Copy:
  - `MOVING_ENDED`: title "Add a moment from just now", body "You just finished moving around. Say what happened while you remember it."
  - `TRIP_ENDED`: title "Add a moment from your trip", body "Say where you went and what happened on the way."
- [ ] **Step 4: Run and confirm the tests pass**, including `:client:domain:compileKotlinIosSimulatorArm64`.
- [ ] **Step 5: Commit** `chore(reminders): Hold reminders during activities and remind when one ends` with Task 3.

### Task 5: The phone feeds and publishes its activity

**Files:**
- Create: `app/compose-main/src/androidMain/kotlin/app/logdate/client/activity/PhoneActivityTransitions.kt` (registration with `ActivityRecognitionClient`, its own `PendingIntent` request code, and `PhoneActivityTransitionReceiver` that maps `DetectedActivity` types: `WALKING`, `RUNNING`, `ON_BICYCLE` → `MOVING`; `IN_VEHICLE` → `IN_VEHICLE`; others ignored)
- Modify: `app/compose-main/src/androidMain/AndroidManifest.xml` (register the receiver, not exported)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/di/PhoneReminderSignalsModule.kt` (`activity = { tracker.agreed.value }`)
- Modify: `app/compose-main/src/androidMain/kotlin/app/logdate/client/sync/v2/PhoneStatePublisher.kt` (`PhoneStateParts.activity`, mirror `ACTIVITY_AWARENESS` in `flags`)
- Modify: `client/datastore/.../FeatureFlag.kt` (`ACTIVITY_AWARENESS`), `client/feature/core/.../labs/LabsFlagCopy.kt` and `strings_labs.xml` (Labs wording; Android only in `existsOn`)
- Test: a Robolectric or host test for the `DetectedActivity` mapping; `PhoneStatePublisherTest` for the new field

- [ ] **Step 1: Write the failing mapping test** (`ActivityTransitionEvent` with `IN_VEHICLE`/`ENTER` → `ActivityTransition(IN_VEHICLE, true, at)`; `STILL` → null).
- [ ] **Step 2: Run and confirm failure.** `./gradlew :app:compose-main:testAndroidHostTest --tests '*PhoneActivityTransition*'`
- [ ] **Step 3: Implement.** Register only while `ACTIVITY_AWARENESS` is on, reminders are on and `ACTIVITY_RECOGNITION` is granted; unregister otherwise. The receiver uses `goAsync()` and calls `tracker.accept` on the app scope. Labs wording: title "Activity awareness", description "Reminders wait while you walk, ride or travel, and your phone and watch notice when you stop. Activity is read on your devices and never leaves them."
- [ ] **Step 4: Run the tests and the phone build.** `./gradlew :app:compose-main:testAndroidHostTest :app:android-main:assembleDebug`
- [ ] **Step 5: Commit** `chore(reminders): Notice activities on the phone behind a Labs flag`.

### Task 6: The watch feeds its tracker from Health Services

**Files:**
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WorkoutEndListenerService.kt` (rename the file and class to `UserActivityListenerService`, keep the manifest entry's class name updated, map `EXERCISE` → `MOVING` and `ASLEEP` → `SLEEP` transitions from each previous/current pair)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/ActivityRegistration.kt` (sleep is optional: require exercise and passive as today)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderDispatcher.kt` (`onActivityEnded(activity)` posts `MOVING_ENDED` with the flag on; `onWorkoutEnded` stays the flag-off path)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/reminders/WatchReminderRules.kt` (the evening check also waits while `agreed.current` holds reminders)
- Modify: `app/wear/src/main/kotlin/app/logdate/wear/sync/v2/WatchOwnState.kt` (publish `activity`), `WatchStateMirror.kt` (mirror `ACTIVITY_AWARENESS` from the phone like `WATCH_EXPERIENCE_V2`), `di/WearV2Module.kt` (the tracker, with `isPhone = false`, the peer from `PeerStateStore`, worn from presence)
- Test: `WorkoutWatcherTest` becomes `UserActivityWatcherTest`; `WatchReminderRulesTest`; `WatchStateMirrorTest`

- [ ] **Step 1: Write the failing tests**: exercise then passive becomes MOVING enter then exit; asleep then passive becomes SLEEP enter then exit; with the flag off, the old workout reminder still posts and the tracker is not fed.
- [ ] **Step 2: Run and confirm failure.** `./gradlew :app:wear:testDebugUnitTest --tests '*Activity*' --tests '*Reminder*'`
- [ ] **Step 3: Implement** as listed under Files.
- [ ] **Step 4: Run the watch unit tests and the emulator reminder tests.** `./gradlew :app:wear:testDebugUnitTest :app:wear:wearDevicesGroupDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.wear.e2e.ReminderNotificationTest,app.logdate.reminders.e2e.ReminderDeliveryRuntimeTest`
- [ ] **Step 5: Commit** `chore(wear): Notice movement and sleep on the watch for reminders`.

## Self-review

- **Spec coverage:** kinds, steadiness constants, one tracker per device, agreement order, the peer field, holding reminders, ending reminders, the compatibility rule for older peers, the flag and Labs wording, permissions: Tasks 1–6. Activity context on moments and the suggestion UI belong to steps 2 and 3.
- **Changes from the spec:** no shared transition feed; holding is `Skip(ACTIVITY_UNDER_WAY)`; two triggers instead of `ACTIVITY_ENDED`. Update the spec's Reminders section to match when Task 4 lands.
- **Type names:** `ActivityLog`, `AgreedActivity`, `ActivitySnapshot`, `agreedActivity`, `latestEnded`, `MOVING_ENDED`, `TRIP_ENDED`, `ACTIVITY_UNDER_WAY` are used consistently across tasks.
