package app.logdate.feature.onboarding.ui

import app.logdate.client.domain.dayboundary.HealthConnectStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingDayBoundariesScreenStateTest {
    @Test
    fun `post-permission action stays idle until enable flow is active`() {
        assertEquals(
            DayBoundariesPostPermissionAction.NONE,
            resolveDayBoundariesPostPermissionAction(
                enableAfterPermission = false,
                completedRequestCount = 1,
                healthConnectStatus = HealthConnectStatus.CONNECTED,
            ),
        )
    }

    @Test
    fun `post-permission action enables and continues once health connect is connected`() {
        assertEquals(
            DayBoundariesPostPermissionAction.ENABLE_AND_CONTINUE,
            resolveDayBoundariesPostPermissionAction(
                enableAfterPermission = true,
                completedRequestCount = 1,
                healthConnectStatus = HealthConnectStatus.CONNECTED,
            ),
        )
    }

    @Test
    fun `post-permission action resets request state when permissions are still needed`() {
        assertEquals(
            DayBoundariesPostPermissionAction.RESET_REQUEST_STATE,
            resolveDayBoundariesPostPermissionAction(
                enableAfterPermission = true,
                completedRequestCount = 1,
                healthConnectStatus = HealthConnectStatus.PERMISSIONS_NEEDED,
            ),
        )
    }

    @Test
    fun `post-permission action waits while health connect status is still checking`() {
        assertEquals(
            DayBoundariesPostPermissionAction.NONE,
            resolveDayBoundariesPostPermissionAction(
                enableAfterPermission = true,
                completedRequestCount = 1,
                healthConnectStatus = HealthConnectStatus.CHECKING,
            ),
        )
    }
}
