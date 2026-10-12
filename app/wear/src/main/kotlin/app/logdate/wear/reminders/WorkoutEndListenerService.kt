package app.logdate.wear.reminders

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Instant

/** The person's activity as reminders read it. */
enum class ActivityKind {
    EXERCISE,
    PASSIVE,
    ASLEEP,
    UNKNOWN,
}

fun UserActivityState.toActivityKind(): ActivityKind =
    when (this) {
        UserActivityState.USER_ACTIVITY_EXERCISE -> ActivityKind.EXERCISE
        UserActivityState.USER_ACTIVITY_PASSIVE -> ActivityKind.PASSIVE
        UserActivityState.USER_ACTIVITY_ASLEEP -> ActivityKind.ASLEEP
        else -> ActivityKind.UNKNOWN
    }

/** When a workout ended: the change from exercise to passive, at [changedAt]. Null for any other change. */
fun workoutEndedAt(
    previous: ActivityKind?,
    current: ActivityKind,
    changedAt: Instant,
): Instant? = changedAt.takeIf { previous == ActivityKind.EXERCISE && current == ActivityKind.PASSIVE }

/** The last activity Health Services reported, across the listener's short lives. */
interface ActivityTransitionStore {
    fun last(): ActivityKind?

    fun lastChangeAt(): Instant?

    fun record(
        kind: ActivityKind,
        changedAt: Instant,
    )
}

/** [ActivityTransitionStore] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. */
class SharedPreferencesActivityTransitionStore(
    private val preferences: SharedPreferences,
) : ActivityTransitionStore {
    override fun last(): ActivityKind? =
        preferences.getString(KEY_LAST, null)?.let { name ->
            ActivityKind.entries.firstOrNull {
                it.name ==
                    name
            }
        }

    override fun lastChangeAt(): Instant? =
        preferences
            .getLong(KEY_CHANGED_AT, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }
            ?.let(Instant::fromEpochMilliseconds)

    override fun record(
        kind: ActivityKind,
        changedAt: Instant,
    ) {
        preferences.edit {
            putString(KEY_LAST, kind.name)
            putLong(KEY_CHANGED_AT, changedAt.toEpochMilliseconds())
        }
    }

    companion object {
        const val PREFERENCES_NAME = "wear_activity"
        private const val KEY_LAST = "last_activity"
        private const val KEY_CHANGED_AT = "last_activity_at"
    }
}

/**
 * Turns activity changes into workout reminders. An unknown reading is skipped, so it never hides the
 * end of a workout that it falls in the middle of.
 */
class WorkoutWatcher(
    private val transitions: ActivityTransitionStore,
    private val dispatcher: WatchReminderDispatcher,
    private val experienceEnabled: () -> Boolean = { true },
    private val awaitExperience: suspend () -> Unit = {},
) {
    private val activation = Mutex()
    private val events = Mutex()

    /** Serializes callbacks while the persisted rollout state loads on a cold process start. */
    suspend fun onActivityWhenReady(
        kind: ActivityKind,
        changedAt: Instant,
    ): Boolean =
        activation.withLock {
            awaitExperience()
            onActivity(kind, changedAt)
        }

    /** @return true when [kind] ended a workout and a reminder was shown. */
    suspend fun onActivity(
        kind: ActivityKind,
        changedAt: Instant,
    ): Boolean =
        events.withLock {
            if (!experienceEnabled() || kind == ActivityKind.UNKNOWN) return@withLock false
            if (transitions.lastChangeAt()?.let { changedAt <= it } == true) return@withLock false
            val endedAt = workoutEndedAt(transitions.last(), kind, changedAt)
            transitions.record(kind, changedAt)
            endedAt != null && dispatcher.onWorkoutEnded(endedAt)
        }
}

/** Health Services binds this while reminders are on and reports each change in the person's activity. */
class WorkoutEndListenerService :
    PassiveListenerService(),
    KoinComponent {
    private val watcher: WorkoutWatcher by inject()
    private val applicationScope: CoroutineScope by inject()

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        val changedAt = Instant.fromEpochMilliseconds(info.stateChangeTime.toEpochMilli())
        applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (watcher.onActivityWhenReady(info.userActivityState.toActivityKind(), changedAt)) {
                Napier.i("A workout ended at $changedAt; a reminder was shown")
            }
        }
    }
}
