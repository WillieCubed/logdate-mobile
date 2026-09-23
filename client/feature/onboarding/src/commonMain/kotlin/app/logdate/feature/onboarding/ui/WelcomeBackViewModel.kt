package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.identity.ObserveUserIdentityUseCase
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.feature.onboarding.flow.OnboardingCompletionCoordinator
import app.logdate.feature.onboarding.flow.OnboardingStep
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val DEFAULT_WELCOME_BACK_NAME = "user"

/**
 * A view model that exposes state for the [WelcomeBackScreen].
 */
class WelcomeBackViewModel(
    private val refreshStreakUseCase: RefreshStreakUseCase,
    observeUserIdentity: ObserveUserIdentityUseCase,
    private val completionCoordinator: OnboardingCompletionCoordinator,
) : ViewModel() {
    val nameState: StateFlow<String> =
        observeUserIdentity()
            .map { identity -> identity.displayName.ifBlank { DEFAULT_WELCOME_BACK_NAME } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = DEFAULT_WELCOME_BACK_NAME,
            )

    init {
        // Signing back in can restore notes, and Profile only reads the cached streak, so this is
        // where the cache catches up with them.
        viewModelScope.launch {
            refreshStreakUseCase()
        }
    }

    suspend fun finishOnboardingOrReportIncompleteStep(
        onFinish: () -> Unit,
        onIncompleteStep: (OnboardingStep) -> Unit,
    ) = completionCoordinator.finishOnboardingOrReportIncompleteStep(onFinish, onIncompleteStep)
}
