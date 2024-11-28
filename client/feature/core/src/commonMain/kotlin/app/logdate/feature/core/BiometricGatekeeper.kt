package app.logdate.feature.core

import kotlinx.coroutines.flow.StateFlow

/**
 * An interface for managing biometric authentication.
 *
 * This may be used to authenticate the user using biometrics, and provides a state flow that can be
 * observed to determine the current state of the app's biometric authentication.
 *
 * Implementations of this interface should handle the platform-specific APIs for biometric
 * authentication.
 */
interface BiometricGatekeeper {
    val authState: StateFlow<AppAuthState>

    /**
     * Performs biometric authentication.
     *
     * If the user has not enabled biometric authentication, the [authState] will be set to
     * [AppAuthState.NO_PROMPT_NEEDED] to indicate that no prompt is needed.
     */
    fun authenticate(
        title: String = "Making sure it's you",
        subtitle: String = "Continue using your fingerprint or face ID",
        cancelLabel: String = "Cancel",
        requireConfirmation: Boolean = false,
        requestEnrollmentIfNecessary: Boolean = true,
        description: String? = null,
    )

    /**
     * Request that the user enroll in biometric authentication.
     */
    fun requestEnrollment()
}


/**
 * A flag representing the current state of the app's biometric authentication.
 *
 * UI components can use this state to determine how to
 */
enum class AppAuthState {
    /**
     * The user has not enabled biometric authentication.
     */
    NO_PROMPT_NEEDED,

    /**
     * The user has enabled biometric authentication, but has not authenticated in the current session.
     */
    REQUIRE_PROMPT,

    /**
     * The user has recently authenticated with biometrics in the current session.
     */
    AUTHENTICATED,

    /**
     * The user has not enrolled in biometric authentication.
     */
    REQUEST_ENROLLMENT,

    /**
     * The user's device requires a security update to use biometric authentication.
     */
    UPDATE_REQUIRED,

    /**
     * The user's device does not support biometric authentication.
     */
    UNSUPPORTED,

    /**
     * An unknown error has occurred.
     *
     * The user should be prompted to restart.
     */
    UNKNOWN,
}