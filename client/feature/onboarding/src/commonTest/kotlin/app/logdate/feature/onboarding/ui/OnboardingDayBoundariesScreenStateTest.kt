package app.logdate.feature.onboarding.ui

import app.logdate.client.domain.dayboundary.HealthConnectGateKind
import app.logdate.client.domain.dayboundary.HealthConnectGateState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the state resolution logic of the onboarding's day boundaries screen.
 *
 * This suite verifies the complex state machine involved in integrating Health Connect,
 * ensuring correct post-permission actions based on gate status, user consent, and
 * whether permission requests are in-flight.
 */
class OnboardingDayBoundariesScreenStateTest {
    @Test
    fun `post-permission action stays idle until enable flow is active`() {
        assertEquals(
            DayBoundariesPostPermissionAction.NONE,
            resolveDayBoundariesPostPermissionAction(
                pendingEnableAfterPermission = false,
                hasPermission = true,
                permissionRequested = true,
                isRequestInFlight = false,
                gateState =
                    HealthConnectGateState(
                        kind = HealthConnectGateKind.READY,
                    ),
            ),
        )
    }

    @Test
    fun `post-permission action enables and continues once gate is ready`() {
        assertEquals(
            DayBoundariesPostPermissionAction.ENABLE_AND_CONTINUE,
            resolveDayBoundariesPostPermissionAction(
                pendingEnableAfterPermission = true,
                hasPermission = true,
                permissionRequested = true,
                isRequestInFlight = false,
                gateState =
                    HealthConnectGateState(
                        kind = HealthConnectGateKind.READY,
                    ),
            ),
        )
    }

    @Test
    fun `post-permission action resets after a rejected permission request`() {
        assertEquals(
            DayBoundariesPostPermissionAction.RESET_REQUEST_STATE,
            resolveDayBoundariesPostPermissionAction(
                pendingEnableAfterPermission = true,
                hasPermission = false,
                permissionRequested = true,
                isRequestInFlight = false,
                gateState =
                    HealthConnectGateState(
                        kind = HealthConnectGateKind.PERMISSION_DENIED,
                    ),
            ),
        )
    }

    @Test
    fun `post-permission action waits while a request is still in flight`() {
        assertEquals(
            DayBoundariesPostPermissionAction.NONE,
            resolveDayBoundariesPostPermissionAction(
                pendingEnableAfterPermission = true,
                hasPermission = false,
                permissionRequested = true,
                isRequestInFlight = true,
                gateState =
                    HealthConnectGateState(
                        kind = HealthConnectGateKind.NEEDS_PERMISSION,
                    ),
            ),
        )
    }

    @Test
    fun `post-permission action waits while the gate is still checking`() {
        assertEquals(
            DayBoundariesPostPermissionAction.NONE,
            resolveDayBoundariesPostPermissionAction(
                pendingEnableAfterPermission = true,
                hasPermission = false,
                permissionRequested = false,
                isRequestInFlight = false,
                gateState =
                    HealthConnectGateState(
                        kind = HealthConnectGateKind.CHECKING,
                    ),
            ),
        )
    }
}
