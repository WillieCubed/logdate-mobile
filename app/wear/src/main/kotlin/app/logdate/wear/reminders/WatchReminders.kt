package app.logdate.wear.reminders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Saved reminder choice and the listener actually registered on this watch. */
class WatchReminders(
    private val preferences: WatchReminderPreferences,
    private val activity: ActivityRegistration,
    private val permissions: ReminderPermissions,
    private val evening: EveningReminderScheduler,
    private val experienceEnabled: () -> Boolean = { true },
    private val awaitExperience: suspend () -> Unit = {},
    private val writeSharedChoice: suspend (Boolean) -> Unit = {},
    private val initializeHours: suspend () -> Unit = {},
) {
    private val registration = Mutex()
    private val currentWorkouts = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = preferences.enabled
    val workoutsActive: StateFlow<Boolean> = currentWorkouts.asStateFlow()

    val isActive: Boolean
        get() = preferences.enabled.value && experienceEnabled() && permissions.notificationsGranted()

    suspend fun awaitActive(): Boolean {
        awaitExperience()
        initializeHours()
        return isActive
    }

    suspend fun setEnabled(enabled: Boolean) {
        writeSharedChoice(enabled)
        preferences.setEnabled(enabled)
        restore()
    }

    /** Reconciles boot, permission and rollout changes with the latest saved choice. */
    suspend fun restore(rescheduleEvening: Boolean = false) {
        awaitExperience()
        initializeHours()
        registration.withLock {
            if (isActive) {
                if (rescheduleEvening) evening.scheduleNext() else evening.ensureScheduled()
            } else {
                evening.cancel()
            }
            if (canListen()) {
                currentWorkouts.value = false
                val registered = activity.register()
                if (canListen()) currentWorkouts.value = registered else stop()
            } else {
                stop()
            }
        }
    }

    private fun canListen(): Boolean = isActive && permissions.activityRecognitionGranted()

    private suspend fun stop() {
        currentWorkouts.value = false
        activity.unregister()
    }
}
