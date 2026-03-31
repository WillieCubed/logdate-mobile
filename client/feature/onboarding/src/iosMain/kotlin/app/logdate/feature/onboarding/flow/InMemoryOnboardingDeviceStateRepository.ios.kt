package app.logdate.feature.onboarding.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryOnboardingDeviceStateRepository : OnboardingDeviceStateRepository {
    private val state = MutableStateFlow(OnboardingDeviceState())

    override val deviceState: Flow<OnboardingDeviceState> = state

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
