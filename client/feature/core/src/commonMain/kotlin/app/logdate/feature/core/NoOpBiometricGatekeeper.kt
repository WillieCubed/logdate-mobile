package app.logdate.feature.core

import app.logdate.client.text.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A no-op [BiometricGatekeeper] for platforms without biometric auth.
 *
 * Desktop does not expose a biometric challenge flow, so authentication is already satisfied.
 */
class NoOpBiometricGatekeeper : BiometricGatekeeper {
    private val _authState = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)
    override val authState: StateFlow<AppAuthState> = _authState

    override fun authenticate(
        title: UiText,
        subtitle: UiText,
        cancelLabel: UiText,
        requireConfirmation: Boolean,
        requestEnrollmentIfNecessary: Boolean,
        description: UiText?,
        onResult: (AppAuthState) -> Unit,
    ) {
        // no-op
    }

    override fun requestEnrollment() {
        // no-op
    }
}
