package app.logdate.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A view model that exposes state for the [WelcomeBackScreen].
 */
class WelcomeBackViewModel : ViewModel() {
    // TODO: Use repository to get user's actual name
    private val _nameState = MutableStateFlow("user")
    val nameState: StateFlow<String> = _nameState
}
