package app.logdate.feature.onboarding.flow

import android.content.Context
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class NoBackupOnboardingDeviceStateRepository(
    context: Context,
) : OnboardingDeviceStateRepository {
    private val notificationsHandledFile = File(context.noBackupFilesDir, NOTIFICATIONS_HANDLED_FILENAME)
    private val activeEntryModeFile = File(context.noBackupFilesDir, ACTIVE_ENTRY_MODE_FILENAME)
    private val state = MutableStateFlow(readCurrentState())

    override val deviceState: Flow<OnboardingDeviceState> = state

    override suspend fun markNotificationsHandled() {
        notificationsHandledFile.parentFile?.mkdirs()
        runCatching {
            if (!notificationsHandledFile.exists()) {
                notificationsHandledFile.createNewFile()
            }
            state.value = readCurrentState()
        }.onFailure { error ->
            Napier.e("Failed to persist onboarding device state", error)
            throw error
        }
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
            notificationsHandledOnThisDevice = notificationsHandledFile.exists(),
            activeEntryMode = readActiveEntryMode(),
        )

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
        private const val NOTIFICATIONS_HANDLED_FILENAME = ".onboarding_notifications_handled"
        private const val ACTIVE_ENTRY_MODE_FILENAME = ".onboarding_entry_mode"
    }
}
