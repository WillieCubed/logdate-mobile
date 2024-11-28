package app.logdate.feature.core.di

import app.logdate.feature.core.AppAuthState
import app.logdate.feature.core.BiometricGatekeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An implementation of [BiometricGatekeeper] that uses native iOS biometric authentication.
 */
class IosBiometricGatekeeper(
) : BiometricGatekeeper {
    private val _authState = MutableStateFlow(AppAuthState.NO_PROMPT_NEEDED)

    override val authState: StateFlow<AppAuthState>
        get() = _authState.asStateFlow()

    override fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
        requireConfirmation: Boolean,
        requestEnrollmentIfNecessary: Boolean,
        description: String?,
    ) {
        TODO("Not yet implemented")
    }

    override fun requestEnrollment() {
        TODO("Not yet implemented")
    }


}