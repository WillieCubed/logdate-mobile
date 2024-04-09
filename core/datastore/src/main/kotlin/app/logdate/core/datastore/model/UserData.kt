package app.logdate.core.datastore.model

import kotlinx.datetime.Instant


/**
 * User metadata.
 */
data class UserData(
    val isOnboarded: Boolean,
    val onboardedDate: Instant,
    val securityLevel: AppSecurityLevel,
    val favoriteNotes: List<String>,
)

/**
 * The level of security for interacting with the app.
 */
enum class AppSecurityLevel {
    /**
     * The user should not be prompted to authenticate on app launch.
     */
    NONE,

    /**
     * The user should be prompted to authenticate with biometrics on app launch.
     */
    BIOMETRIC,

    /**
     * The user should be prompted to authenticate with a password on app launch.
     */
    PASSWORD,
}
