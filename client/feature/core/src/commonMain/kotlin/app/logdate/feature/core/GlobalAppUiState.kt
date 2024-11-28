package app.logdate.feature.core

/**
 * UI state for properties that span the entire client app UX.
 */
data class GlobalAppUiState(
    val isLoaded: Boolean = false,
    /**
     * Indicates whether the app is currently connected to the internet.
     */
    val isOnline: Boolean = false,
    /**
     * The current authentication state of the app.
     *
     * This can be used to determine whether the user needs to authenticate (e.g. use biometrics
     * or a password) to access the app.
     */
    val authState: AppAuthState = AppAuthState.NO_PROMPT_NEEDED,
    /**
     * Whether the user has set up the app for their use.
     *
     * If this is false, the user will be prompted to set up the app when they first open it.
     */
    val isOnboarded: Boolean = false,
)

val GlobalAppUiState.requiresUnlock: Boolean
    get() = authState == AppAuthState.REQUIRE_PROMPT

val GlobalAppUiState.isAppUnlocked: Boolean
    // TODO: Test that other app states don't break the app
    get() = authState == AppAuthState.NO_PROMPT_NEEDED || authState == AppAuthState.AUTHENTICATED