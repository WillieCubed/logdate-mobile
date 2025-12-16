package app.logdate.feature.core

import app.logdate.feature.core.BiometricGatekeeper.AuthResult

/**
 * iOS implementation of the BiometricGatekeeper.
 * Currently uses a stub implementation, to be replaced with actual
 * iOS biometric authentication in the future.
 */
class IosBiometricGatekeeper : BiometricGatekeeper {
    override suspend fun authenticate(title: String?, subtitle: String?): AuthResult {
        // Stub implementation always succeeds
        return AuthResult.Success
    }

    override suspend fun isAvailable(): Boolean {
        // For now, just return true to indicate biometrics is available
        return true
    }
}