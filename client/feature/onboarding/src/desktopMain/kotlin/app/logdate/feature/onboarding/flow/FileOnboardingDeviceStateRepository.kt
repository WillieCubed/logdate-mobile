package app.logdate.feature.onboarding.flow

import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Desktop implementation of [OnboardingDeviceStateRepository] that persists state to marker files
 * in `~/.logdate/onboarding/`.
 *
 * This mirrors the Android [NoBackupOnboardingDeviceStateRepository] pattern — lightweight marker
 * files that survive app restarts so users aren't forced through onboarding every launch.
 */
class FileOnboardingDeviceStateRepository : OnboardingDeviceStateRepository {
    private val stateDir = File(System.getProperty("user.home"), ".logdate/onboarding")
    private val recommendationsHandledFile = File(stateDir, RECOMMENDATIONS_HANDLED_FILENAME)
    private val dayBoundariesHandledFile = File(stateDir, DAY_BOUNDARIES_HANDLED_FILENAME)
    private val locationHandledFile = File(stateDir, LOCATION_HANDLED_FILENAME)
    private val notificationsHandledFile = File(stateDir, NOTIFICATIONS_HANDLED_FILENAME)
    private val activeEntryModeFile = File(stateDir, ACTIVE_ENTRY_MODE_FILENAME)
    private val state = MutableStateFlow(readCurrentState())

    override val deviceState: StateFlow<OnboardingDeviceState> = state

    override suspend fun markRecommendationsHandled() {
        persistMarkerFile(recommendationsHandledFile)
    }

    override suspend fun markDayBoundariesHandled() {
        persistMarkerFile(dayBoundariesHandledFile)
    }

    override suspend fun markLocationHandled() {
        persistMarkerFile(locationHandledFile)
    }

    override suspend fun markNotificationsHandled() {
        persistMarkerFile(notificationsHandledFile)
    }

    override suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode) {
        runCatching {
            stateDir.mkdirs()
            activeEntryModeFile.writeText(entryMode.name)
            state.value = readCurrentState()
        }.onFailure { error ->
            Napier.e("Failed to persist onboarding entry mode", error)
            throw error
        }
    }

    override suspend fun clear() {
        runCatching {
            listOf(
                recommendationsHandledFile,
                dayBoundariesHandledFile,
                locationHandledFile,
                notificationsHandledFile,
                activeEntryModeFile,
            ).forEach { file ->
                if (file.exists()) file.delete()
            }
            state.value = readCurrentState()
        }.onFailure { error ->
            Napier.e("Failed to clear onboarding device state", error)
            throw error
        }
    }

    private fun readCurrentState(): OnboardingDeviceState =
        OnboardingDeviceState(
            recommendationsHandledOnThisDevice = recommendationsHandledFile.exists(),
            dayBoundariesHandledOnThisDevice = dayBoundariesHandledFile.exists(),
            locationHandledOnThisDevice = locationHandledFile.exists(),
            notificationsHandledOnThisDevice = notificationsHandledFile.exists(),
            activeEntryMode = readActiveEntryMode(),
        )

    private fun persistMarkerFile(file: File) {
        runCatching {
            stateDir.mkdirs()
            if (!file.exists()) {
                file.createNewFile()
            }
            state.value = readCurrentState()
        }.onFailure { error ->
            Napier.e("Failed to persist onboarding device state", error)
            throw error
        }
    }

    private fun readActiveEntryMode(): OnboardingEntryMode =
        runCatching {
            if (!activeEntryModeFile.exists()) {
                return OnboardingEntryMode.FRESH
            }
            OnboardingEntryMode.valueOf(activeEntryModeFile.readText().trim())
        }.getOrElse { error ->
            Napier.w("Failed to read onboarding entry mode, defaulting to fresh", error)
            OnboardingEntryMode.FRESH
        }

    private companion object {
        private const val RECOMMENDATIONS_HANDLED_FILENAME = ".onboarding_recommendations_handled"
        private const val DAY_BOUNDARIES_HANDLED_FILENAME = ".onboarding_day_boundaries_handled"
        private const val LOCATION_HANDLED_FILENAME = ".onboarding_location_handled"
        private const val NOTIFICATIONS_HANDLED_FILENAME = ".onboarding_notifications_handled"
        private const val ACTIVE_ENTRY_MODE_FILENAME = ".onboarding_entry_mode"
    }
}
