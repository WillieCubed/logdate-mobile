package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.streak.RefreshStreakUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A view model that exposes state for the [WelcomeBackScreen].
 */
class WelcomeBackViewModel(
    // TODO: Use repository to get user's actual name
    private val refreshStreakUseCase: RefreshStreakUseCase,
) : ViewModel() {
    private val _nameState = MutableStateFlow("user")
    val nameState: StateFlow<String> = _nameState

    init {
        viewModelScope.launch {
            refreshStreakUseCase()
        }
    }
}
