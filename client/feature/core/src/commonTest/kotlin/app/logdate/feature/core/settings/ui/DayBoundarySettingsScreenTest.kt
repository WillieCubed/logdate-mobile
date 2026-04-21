package app.logdate.feature.core.settings.ui

import app.logdate.client.domain.dayboundary.HealthConnectGateKind
import app.logdate.client.domain.dayboundary.HealthConnectGateState
import app.logdate.client.domain.dayboundary.HealthConnectMissingRequirement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the visibility and behavior of the sleep-based day boundary settings UI.
 *
 * This suite ensures that the sleep-based toggle is only exposed when the underlying
 * Health Connect "gate" is in a ready state, correctly hiding it during checks or
 * when requirements (like permissions) are missing.
 */
class DayBoundarySettingsScreenTest {
    @Test
    fun `ready gate shows the real toggle`() {
        assertTrue(
            shouldShowSleepBasedToggle(
                HealthConnectGateState(
                    kind = HealthConnectGateKind.READY,
                ),
            ),
        )
    }

    @Test
    fun `checking gate hides the real toggle`() {
        assertFalse(
            shouldShowSleepBasedToggle(
                HealthConnectGateState(
                    kind = HealthConnectGateKind.CHECKING,
                ),
            ),
        )
    }

    @Test
    fun `recovery gate hides the real toggle`() {
        assertFalse(
            shouldShowSleepBasedToggle(
                HealthConnectGateState(
                    kind = HealthConnectGateKind.RECOVERY_REQUIRED,
                    missingRequirement = HealthConnectMissingRequirement.PERMISSION,
                ),
            ),
        )
    }

    @Test
    fun `denied gate hides the real toggle`() {
        assertFalse(
            shouldShowSleepBasedToggle(
                HealthConnectGateState(
                    kind = HealthConnectGateKind.PERMISSION_DENIED,
                    missingRequirement = HealthConnectMissingRequirement.PERMISSION,
                ),
            ),
        )
    }
}
