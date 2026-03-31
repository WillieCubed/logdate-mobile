package app.logdate.feature.onboarding.flow

import kotlinx.coroutines.flow.Flow

data class OnboardingDeviceState(
    val notificationsHandledOnThisDevice: Boolean = false,
    val activeEntryMode: OnboardingEntryMode = OnboardingEntryMode.FRESH,
)

interface OnboardingDeviceStateRepository {
    val deviceState: Flow<OnboardingDeviceState>

    suspend fun markNotificationsHandled()

    suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode)

    suspend fun clear()
}
