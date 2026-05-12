package app.logdate.wear.presentation.onboarding

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingPermissionsUiStateTest {
    @Test
    fun `nothing granted - allow button visible and no skip path`() {
        val state = OnboardingPermissionsUiState(micGranted = false, locationGranted = false)

        assertFalse(state.allRequiredGranted)
        assertTrue(state.showAllowButton)
        assertFalse(
            state.showSkipLocationButton,
            "skip should never appear before the user grants the watch's required mic permission",
        )
    }

    @Test
    fun `mic granted only - allow stays visible alongside Maybe later`() {
        val state = OnboardingPermissionsUiState(micGranted = true, locationGranted = false)

        assertFalse(state.allRequiredGranted)
        assertTrue(state.showAllowButton, "user can still grant location via the allow button")
        assertTrue(
            state.showSkipLocationButton,
            "with mic granted the Maybe later affordance becomes available",
        )
    }

    @Test
    fun `location granted only - allow button stays, skip stays hidden`() {
        val state = OnboardingPermissionsUiState(micGranted = false, locationGranted = true)

        assertFalse(state.allRequiredGranted)
        assertTrue(state.showAllowButton)
        assertFalse(
            state.showSkipLocationButton,
            "without mic the user cannot bypass onboarding by skipping location",
        )
    }

    @Test
    fun `both granted - hide both buttons and signal auto-advance`() {
        val state = OnboardingPermissionsUiState(micGranted = true, locationGranted = true)

        assertTrue(state.allRequiredGranted)
        assertFalse(state.showAllowButton)
        assertFalse(state.showSkipLocationButton)
    }
}
