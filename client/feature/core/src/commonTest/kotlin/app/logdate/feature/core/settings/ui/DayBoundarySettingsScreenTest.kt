package app.logdate.feature.core.settings.ui

import app.logdate.client.domain.dayboundary.HealthConnectStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class DayBoundarySettingsScreenTest {
    @Test
    fun `disabling sleep based boundaries always maps to disable`() {
        assertEquals(
            DayBoundaryToggleAction.DISABLE,
            resolveDayBoundaryToggleAction(
                enabled = false,
                healthConnectStatus = HealthConnectStatus.CHECKING,
            ),
        )
    }

    @Test
    fun `enabling with connected health connect maps to direct enable`() {
        assertEquals(
            DayBoundaryToggleAction.ENABLE_DIRECTLY,
            resolveDayBoundaryToggleAction(
                enabled = true,
                healthConnectStatus = HealthConnectStatus.CONNECTED,
            ),
        )
    }

    @Test
    fun `enabling while permissions are needed requests permissions`() {
        assertEquals(
            DayBoundaryToggleAction.REQUEST_PERMISSIONS,
            resolveDayBoundaryToggleAction(
                enabled = true,
                healthConnectStatus = HealthConnectStatus.PERMISSIONS_NEEDED,
            ),
        )
    }

    @Test
    fun `enabling while health connect is still checking requests permissions`() {
        assertEquals(
            DayBoundaryToggleAction.REQUEST_PERMISSIONS,
            resolveDayBoundaryToggleAction(
                enabled = true,
                healthConnectStatus = HealthConnectStatus.CHECKING,
            ),
        )
    }

    @Test
    fun `enabling when health connect is unavailable is a no-op`() {
        assertEquals(
            DayBoundaryToggleAction.NO_OP,
            resolveDayBoundaryToggleAction(
                enabled = true,
                healthConnectStatus = HealthConnectStatus.NOT_AVAILABLE,
            ),
        )
    }
}
