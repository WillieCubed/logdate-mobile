package app.logdate.feature.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A stub implementation of [BiometricGatekeeper] that does nothing.
 *
 * This can be used for tests or for platforms where biometric authentication is not supported.
 */
class StubBiometricGatekeeper : BiometricGatekeeper {
    override val authState: StateFlow<AppAuthState>
        get() = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

    override fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
        requireConfirmation: Boolean,
        requestEnrollmentIfNecessary: Boolean,
        description: String?,
    ) {
        // no-op
    }

    override fun requestEnrollment() {
        // no-op
    }
}