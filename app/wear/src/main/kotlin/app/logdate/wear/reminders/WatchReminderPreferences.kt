package app.logdate.wear.reminders

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/** Whether reminders are on for this watch, and when it last sent one. */
interface WatchReminderPreferences {
    val enabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean)

    fun lastReminderAt(): Instant?

    fun observeLastReminderAt(): Flow<Instant?> = flowOf(lastReminderAt())

    fun setLastReminderAt(at: Instant)

    fun mergePhoneReminderAt(
        nodeId: String,
        at: Long,
        observedAt: Instant,
    )
}

/** [WatchReminderPreferences] in the watch's own SharedPreferences file, [PREFERENCES_NAME]. Reminders start off. */
class SharedPreferencesWatchReminderPreferences(
    private val preferences: SharedPreferences,
) : WatchReminderPreferences {
    @Synchronized
    override fun mergePhoneReminderAt(
        nodeId: String,
        at: Long,
        observedAt: Instant,
    ) {
        val sourceKey = "phone_reminder_at:$nodeId"
        if (at <= 0 || at <= preferences.getLong(sourceKey, NONE)) return
        val observed = Instant.fromEpochMilliseconds(minOf(at, observedAt.toEpochMilliseconds()))
        val latest = lastReminderAt()?.let { maxOf(it, observed) } ?: observed
        val saved =
            preferences
                .edit()
                .putLong(sourceKey, at)
                .putLong(KEY_LAST_REMINDER_AT, latest.toEpochMilliseconds())
                .commit()
        check(saved) { "The phone's reminder time could not be saved" }
        posted.value = latest
    }

    private val current = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    private val posted = MutableStateFlow(lastReminderAt())

    override val enabled: StateFlow<Boolean> = current.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, enabled) }
        current.value = enabled
    }

    override fun lastReminderAt(): Instant? =
        preferences.getLong(KEY_LAST_REMINDER_AT, NONE).takeIf { it != NONE }?.let(Instant::fromEpochMilliseconds)

    override fun observeLastReminderAt(): Flow<Instant?> = posted.asStateFlow()

    @Synchronized
    override fun setLastReminderAt(at: Instant) {
        if (lastReminderAt()?.let { it >= at } == true) return
        preferences.edit { putLong(KEY_LAST_REMINDER_AT, at.toEpochMilliseconds()) }
        posted.value = at
    }

    companion object {
        const val PREFERENCES_NAME = "wear_reminders"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_REMINDER_AT = "last_reminder_at"
        private const val NONE = -1L
    }
}
