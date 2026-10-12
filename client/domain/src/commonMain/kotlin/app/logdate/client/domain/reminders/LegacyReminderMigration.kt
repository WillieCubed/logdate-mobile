package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/** Captures eligibility before this process can complete a fresh onboarding flow. */
class LegacyReminderMigration(
    private val storage: KeyValueStorage,
    private val preferences: StampedPreferenceStore,
    private val userData: Flow<UserData>,
    private val legacy: MemoriesSettingsRepository,
    clock: Clock = Clock.System,
) {
    private val startedAt = clock.now()
    private val mutex = Mutex()
    private var completed = false

    suspend fun ensure(): Boolean =
        mutex.withLock {
            if (completed) return@withLock true
            val eligibility = storage.getString(STORAGE_KEY) ?: snapshotEligibility() ?: return@withLock false
            val enabled =
                when (eligibility) {
                    "on" -> true
                    "off" -> false
                    else -> return@withLock false
                }
            completed = preferences.initializeMissing(SharedPreferenceKey.RemindersEnabled.name, JsonPrimitive(enabled))
            completed
        }

    private suspend fun snapshotEligibility(): String? {
        val user = userData.first()
        val settings = legacy.getSettings()
        val established = user.isOnboarded && user.onboardedDate < startedAt
        val enabled =
            established &&
                settings.contextualRecommendationsEnabled &&
                settings.ambientPromptsEnabled &&
                settings.captureNudgesEnabled
        val eligibility = if (enabled) "on" else "off"
        storage.putString(STORAGE_KEY, eligibility)
        return eligibility.takeIf { storage.getString(STORAGE_KEY) == eligibility }
    }

    companion object {
        const val STORAGE_KEY = "reminders_legacy_eligibility_v1"
    }
}
