package app.logdate.wear.reminders

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/** Whether reminders are on for this watch, and when it last sent one. */
interface WatchReminderPreferences {
    val enabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)

    fun lastReminderAt(): Instant?

    fun setLastReminderAt(at: Instant)
}

/** [WatchReminderPreferences] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. Reminders start off. */
class SharedPreferencesWatchReminderPreferences(
    private val preferences: SharedPreferences,
) : WatchReminderPreferences {
    private val current = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))

    override val enabled: StateFlow<Boolean> = current.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        current.value = enabled
    }

    override fun lastReminderAt(): Instant? =
        preferences.getLong(KEY_LAST_REMINDER_AT, NONE).takeIf { it != NONE }?.let(Instant::fromEpochMilliseconds)

    override fun setLastReminderAt(at: Instant) {
        preferences.edit { putLong(KEY_LAST_REMINDER_AT, at.toEpochMilliseconds()) }
    }

    companion object {
        const val PREFERENCES_NAME = "wear_reminders"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_REMINDER_AT = "last_reminder_at"
        private const val NONE = -1L
    }
}
