package app.logdate.feature.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS implementation of the BiometricGatekeeper.
 * Currently uses a stub implementation, to be replaced with native biometrics.
 */
class IosBiometricGatekeeper : BiometricGatekeeper {
    private val _authState = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)
    override val authState: StateFlow<AppAuthState> = _authState

    override fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
        requireConfirmation: Boolean,
        requestEnrollmentIfNecessary: Boolean,
        description: String?,
    ) {
        // no-op until native biometrics are implemented for iOS
    }

    override fun requestEnrollment() {
        // no-op until native biometrics are implemented for iOS
    }
}
