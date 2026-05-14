package app.logdate.client.domain.account

import app.logdate.client.permissions.EmailVerificationManager

/**
 * Combines the platform-capability flag exposed by [EmailVerificationManager]
 * with the server-controlled `email_verification` entitlement to a single
 * is-it-available check that UI surfaces query.
 *
 * The check stays additive — both signals must be on for the entry point to
 * appear. The platform gate hides the row on iOS/Desktop/Wear/Android <34; the
 * server gate lets us kill-switch the feature remotely while the alpha API
 * stabilises without shipping a client.
 *
 * Mirrors the pattern in
 * `client/domain/.../rewind/EntitlementRewindAIAvailability.kt`.
 */
class EmailVerificationAvailability(
    private val manager: EmailVerificationManager,
    private val getCurrentEntitlement: GetCurrentEntitlementUseCase,
) {
    suspend fun isAvailable(): Boolean {
        if (!manager.isSupported) return false
        val entitlement =
            when (val result = getCurrentEntitlement()) {
                is GetCurrentEntitlementUseCase.Result.Success -> result.entitlement
                else -> return false
            }
        return entitlement.features[FEATURE_EMAIL_VERIFICATION] == true
    }

    companion object {
        /** Server-side feature flag that surfaces email verification entry points. */
        const val FEATURE_EMAIL_VERIFICATION = "email_verification"
    }
}
