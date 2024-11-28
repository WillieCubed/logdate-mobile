package app.logdate.shared.model.user

import kotlinx.datetime.Instant


/**
 * User metadata.
 */
data class UserData(
    val birthday: Instant = Instant.DISTANT_PAST,
    val isOnboarded: Boolean = false,
    val onboardedDate: Instant = Instant.DISTANT_PAST,
    val securityLevel: AppSecurityLevel = AppSecurityLevel.NONE,
    val favoriteNotes: List<String> = emptyList(),
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
