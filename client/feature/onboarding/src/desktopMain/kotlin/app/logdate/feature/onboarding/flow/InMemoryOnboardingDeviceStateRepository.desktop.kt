package app.logdate.feature.onboarding.flow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryOnboardingDeviceStateRepository : OnboardingDeviceStateRepository {
    private val state = MutableStateFlow(OnboardingDeviceState())

    override val deviceState: StateFlow<OnboardingDeviceState> = state

    override suspend fun markRecommendationsHandled() {
        state.value = state.value.copy(recommendationsHandledOnThisDevice = true)
    }

    override suspend fun markDayBoundariesHandled() {
        state.value = state.value.copy(dayBoundariesHandledOnThisDevice = true)
    }

    override suspend fun markLocationHandled() {
        state.value = state.value.copy(locationHandledOnThisDevice = true)
    }

    override suspend fun markNotificationsHandled() {
        state.value = state.value.copy(notificationsHandledOnThisDevice = true)
    }

    override suspend fun setActiveEntryMode(entryMode: OnboardingEntryMode) {
        state.value = state.value.copy(activeEntryMode = entryMode)
    }

    override suspend fun clear() {
        state.value = OnboardingDeviceState()
    }
}
