package app.logdate.client.domain.dayboundary

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Validates the state reduction logic for Health Connect gates via [reduceHealthConnectGateState].
 *
 * These tests ensure that the UI can correctly determine whether to show a setup prompt,
 * a permission request, a recovery flow, or if the feature is ready, based on a combination
 * of user preferences, system availability, and permission status.
 */
class HealthConnectGateStateTest {
    @Test
    fun `connected status resolves ready gate`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.READY,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = false,
                healthConnectStatus = HealthConnectStatus.CONNECTED,
                hasPermission = true,
                permissionRequested = false,
            ),
        )
    }

    @Test
    fun `permission status resolves ready when local permission state is already granted`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.READY,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = false,
                healthConnectStatus = HealthConnectStatus.PERMISSIONS_NEEDED,
                hasPermission = true,
                permissionRequested = true,
            ),
        )
    }

    @Test
    fun `permission status resolves denied after a rejected permission request`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.PERMISSION_DENIED,
                missingRequirement = HealthConnectMissingRequirement.PERMISSION,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = false,
                healthConnectStatus = HealthConnectStatus.PERMISSIONS_NEEDED,
                hasPermission = false,
                permissionRequested = true,
            ),
        )
    }

    @Test
    fun `enabled preference with lost permission resolves recovery gate`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.RECOVERY_REQUIRED,
                missingRequirement = HealthConnectMissingRequirement.PERMISSION,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = true,
                healthConnectStatus = HealthConnectStatus.PERMISSIONS_NEEDED,
                hasPermission = false,
                permissionRequested = true,
            ),
        )
    }

    @Test
    fun `enabled preference with required setup resolves recovery gate`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.RECOVERY_REQUIRED,
                missingRequirement = HealthConnectMissingRequirement.SETUP,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = true,
                healthConnectStatus = HealthConnectStatus.PROVIDER_UPDATE_REQUIRED,
                hasPermission = false,
                permissionRequested = false,
            ),
        )
    }

    @Test
    fun `disabled preference with unavailable health connect resolves unavailable gate`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.UNAVAILABLE,
                missingRequirement = HealthConnectMissingRequirement.UNAVAILABLE,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = false,
                healthConnectStatus = HealthConnectStatus.NOT_AVAILABLE,
                hasPermission = false,
                permissionRequested = false,
            ),
        )
    }

    @Test
    fun `checking status preserves the previous resolved gate`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.PERMISSION_DENIED,
                missingRequirement = HealthConnectMissingRequirement.PERMISSION,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = false,
                healthConnectStatus = HealthConnectStatus.CHECKING,
                hasPermission = false,
                permissionRequested = true,
                previousResolvedGateState =
                    HealthConnectGateState(
                        kind = HealthConnectGateKind.PERMISSION_DENIED,
                        missingRequirement = HealthConnectMissingRequirement.PERMISSION,
                    ),
            ),
        )
    }

    @Test
    fun `checking status falls back to checking when no resolved state exists`() {
        assertEquals(
            HealthConnectGateState(
                kind = HealthConnectGateKind.CHECKING,
            ),
            reduceHealthConnectGateState(
                sleepBasedPreferenceEnabled = false,
                healthConnectStatus = HealthConnectStatus.CHECKING,
                hasPermission = false,
                permissionRequested = false,
            ),
        )
    }
}
