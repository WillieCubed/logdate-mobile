package app.logdate.client.reminders

import android.Manifest
import app.logdate.client.domain.reminders.ReminderSignalObserver
import app.logdate.client.permissions.AndroidPermissionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class ReminderPlaceAvailabilityTest {
    @Test fun grantingAndRevokingPermissionRefreshesLocationObservationWithoutRestart() =
        runTest {
            val application = RuntimeEnvironment.getApplication()
            val platform = shadowOf(application)
            platform.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            val enabled = MutableStateFlow(true)
            val consent = MutableStateFlow(true)
            val locations = MutableSharedFlow<Unit>()
            val observer =
                ReminderSignalObserver(
                    enabled,
                    reminderPlaceAvailability(enabled, consent, AndroidPermissionManager(application)),
                    { MutableSharedFlow<Unit>() },
                    { locations },
                    {},
                    backgroundScope,
                )
            observer.start()
            runCurrent()
            assertEquals(0, locations.subscriptionCount.value)
            platform.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            advanceTimeBy(30_001)
            assertEquals(1, locations.subscriptionCount.value)
            platform.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            advanceTimeBy(30_000)
            assertEquals(0, locations.subscriptionCount.value)
        }

    @Test fun consentChangesCloseAndReopenLocationAvailability() =
        runTest {
            val application = RuntimeEnvironment.getApplication()
            shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            val enabled = MutableStateFlow(false)
            val consent = MutableStateFlow(true)
            val results = mutableListOf<Boolean>()
            backgroundScope.launch {
                reminderPlaceAvailability(enabled, consent, AndroidPermissionManager(application)).collect { results.add(it) }
            }
            runCurrent()
            enabled.value = true
            runCurrent()
            consent.value = false
            runCurrent()
            consent.value = true
            runCurrent()
            enabled.value = false
            runCurrent()
            assertEquals(listOf(false, true, false, true, false), results)
        }
}
