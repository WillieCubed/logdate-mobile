package app.logdate.feature.onboarding.flow

import kotlinx.coroutines.flow.StateFlow

data class OnboardingDeviceState(
    val recommendationsHandledOnThisDevice: Boolean = false,
    val dayBoundariesHandledOnThisDevice: Boolean = false,
    val locationHandledOnThisDevice: Boolean = false,
    val notificationsHandledOnThisDevice: Boolean = false,
    val activeEntryMode: OnboardingEntryMode = OnboardingEntryMode.FRESH,
)

interface OnboardingDeviceStateRepository {
    val deviceState: StateFlow<OnboardingDeviceState>

    suspend fun markRecommendationsHandled()

    suspend fun markDayBoundariesHandled()

    suspend fun markLocationHandled()

    suspend fun markNotificationsHandled()

    suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode)

    suspend fun clear()
}
