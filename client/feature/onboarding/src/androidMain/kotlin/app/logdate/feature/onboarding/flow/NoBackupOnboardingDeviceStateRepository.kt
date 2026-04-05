package app.logdate.feature.onboarding.flow

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class NoBackupOnboardingDeviceStateRepository(
    context: Context,
) : OnboardingDeviceStateRepository {
    private val recommendationsHandledFile = File(context.noBackupFilesDir, RECOMMENDATIONS_HANDLED_FILENAME)
    private val dayBoundariesHandledFile = File(context.noBackupFilesDir, DAY_BOUNDARIES_HANDLED_FILENAME)
    private val locationHandledFile = File(context.noBackupFilesDir, LOCATION_HANDLED_FILENAME)
    private val notificationsHandledFile = File(context.noBackupFilesDir, NOTIFICATIONS_HANDLED_FILENAME)
    private val activeEntryModeFile = File(context.noBackupFilesDir, ACTIVE_ENTRY_MODE_FILENAME)
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
            activeEntryModeFile.parentFile?.mkdirs()
            activeEntryModeFile.writeText(entryMode.name)
            state.value = readCurrentState()
        }.onFailure { error ->
            Napier.e("Failed to persist onboarding entry mode", error)
            throw error
        }
    }

    override suspend fun clear() {
        runCatching {
            if (notificationsHandledFile.exists()) {
                notificationsHandledFile.delete()
            }
            if (recommendationsHandledFile.exists()) {
                recommendationsHandledFile.delete()
            }
            if (dayBoundariesHandledFile.exists()) {
                dayBoundariesHandledFile.delete()
            }
            if (locationHandledFile.exists()) {
                locationHandledFile.delete()
            }
            if (activeEntryModeFile.exists()) {
                activeEntryModeFile.delete()
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
        file.parentFile?.mkdirs()
        runCatching {
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
