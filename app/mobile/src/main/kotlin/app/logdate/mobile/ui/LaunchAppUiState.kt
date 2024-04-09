package app.logdate.mobile.ui

/**
 * A representation of app-wide UI state.
 */
sealed interface LaunchAppUiState {
    /**
     * A state representing that the app is still loading.
     */
    data object Loading : LaunchAppUiState

    /**
     * A state representing that the app has finished loading.
     */
    data class Loaded(
        /**
         * A flag indicating whether the user has completed the onboarding process.
         *
         * Observers should check this flag to determine whether to show the onboarding screen on
         * app launch.
         */
        val isOnboarded: Boolean = false,
        /**
         * A flag indicating whether the user has enabled biometric authentication.
         */
        val isBiometricEnabled: Boolean = false,
        /**
         * A flag indicating whether the user has recently authenticated with biometrics in the
         * current session.
         *
         * This will always be true if [isBiometricEnabled] is false.
         */
        val windowIsSecure: Boolean = false,
    ) : LaunchAppUiState
}