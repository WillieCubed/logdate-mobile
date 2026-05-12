package app.logdate.wear.presentation.onboarding

/**
 * Pure UI state for the onboarding permissions page.
 *
 * Captures the three branches the screen renders based on which Android permissions the user has
 * granted. Lives outside the composable so the location-optional "Maybe later" branch has a
 * unit-testable shape — the predicate logic doesn't need an Android runtime to verify.
 */
internal data class OnboardingPermissionsUiState(
    val micGranted: Boolean,
    val locationGranted: Boolean,
) {
    /** All required-or-encouraged permissions are granted; the screen should auto-advance. */
    val allRequiredGranted: Boolean get() = micGranted && locationGranted

    /** Show the primary "Allow permissions" button. */
    val showAllowButton: Boolean get() = !allRequiredGranted

    /**
     * Show the secondary "Maybe later" button so the user can continue without location.
     *
     * Mic is required for recording; location is strongly encouraged but optional. We only
     * surface the skip affordance once the user has granted mic — before that, "Maybe later"
     * would let them advance into the app without the one permission the watch genuinely needs.
     */
    val showSkipLocationButton: Boolean get() = micGranted && !locationGranted
}
