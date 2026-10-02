package app.logdate.wear.presentation.onboarding

import android.Manifest
import android.os.Build

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

/**
 * The permissions the onboarding Allow button asks for in one prompt sequence.
 *
 * Notifications are included from Android 13, where the system hides the recording notification
 * until the user allows them. Neither notifications nor location gate onboarding.
 */
internal fun onboardingPermissionRequest(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> =
    buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
