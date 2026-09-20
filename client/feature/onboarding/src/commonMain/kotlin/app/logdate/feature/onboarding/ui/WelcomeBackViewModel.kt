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
    private val refreshStreakUseCase: RefreshStreakUseCase,
) : ViewModel() {
    // TODO: Use repository to get user's actual name
    private val _nameState = MutableStateFlow("user")
    val nameState: StateFlow<String> = _nameState

    init {
        // Signing back in can restore notes, and Profile only reads the cached streak, so this is
        // where the cache catches up with them.
        viewModelScope.launch {
            refreshStreakUseCase()
        }
    }
}
