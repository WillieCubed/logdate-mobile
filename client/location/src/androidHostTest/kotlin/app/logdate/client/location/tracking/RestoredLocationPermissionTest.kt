package app.logdate.client.location.tracking

import android.content.Context
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RestoredLocationPermissionTest {
    @Test
    fun `restored active tracking resumes after external grant without replacing its schedule on every resume`() =
        runTest {
            val context = mockk<Context>()
            val scheduled = mockk<ScheduledLocationTrackingService>(relaxed = true)
            val passive = mockk<OptimizedBackgroundLocationRegistrar>(relaxed = true)
            val settings = LocationTrackingSettings(backgroundTrackingEnabled = true, captureMode = LocationCaptureMode.ACTIVE)
            val repository = mockk<LocationTrackingSettingsRepository>()
            coEvery { repository.getSettings() } returns settings
            every { repository.observeSettings() } returns MutableStateFlow(settings)
            val permissions = mockk<PermissionManager>()
            var granted = false
            every { permissions.isPermissionGranted(PermissionType.LOCATION) } answers { granted }
            var passiveStarts = 0
            every { passive.start(any()) } answers { if (granted) passiveStarts++ }
            var foregroundStarts = 0
            var foregroundRunning = false
            mockkStatic("app.logdate.client.location.tracking.ActivityAwareLocationServiceKt")
            try {
                every { context.startActivityAwareLocationService(permissions) } answers {
                    if (granted && !foregroundRunning) {
                        foregroundStarts++
                        foregroundRunning = true
                    }
                    granted
                }
                every { context.stopActivityAwareLocationService() } answers { foregroundRunning = false }
                val manager = LocationTrackingManager(context, scheduled, passive, repository, permissions, backgroundScope)
                runCurrent()
                assertEquals(0, foregroundStarts)
                assertEquals(0, passiveStarts)
                verify(exactly = 0) { scheduled.startScheduledTracking(any(), any()) }
                granted = true
                manager.onActivityResumed()
                runCurrent()
                assertEquals(1, foregroundStarts)
                assertEquals(1, passiveStarts)
                manager.onActivityResumed()
                runCurrent()
                assertEquals(1, foregroundStarts)
                assertEquals(1, passiveStarts)
                advanceTimeBy(1_100)
                runCurrent()
                verify(exactly = 1) { scheduled.startScheduledTracking(any(), any()) }
                assertEquals(1, passiveStarts)
                granted = false
                manager.onActivityResumed()
                runCurrent()
                verify(exactly = 2) { scheduled.stopScheduledTracking() }
                granted = true
                manager.onActivityResumed()
                runCurrent()
                assertEquals(2, passiveStarts)
                assertEquals(2, foregroundStarts)
                verify(exactly = 2) { scheduled.startScheduledTracking(any(), any()) }
                coEvery { repository.getSettings() } returns settings.copy(backgroundTrackingEnabled = false)
                manager.onActivityResumed()
                runCurrent()
                assertEquals(false, foregroundRunning, "A persisted pause takes precedence over a debounced active snapshot")
                verify(exactly = 3) { scheduled.stopScheduledTracking() }
                coEvery { repository.getSettings() } throws IllegalStateException("Temporarily unavailable")
                manager.onActivityResumed()
                runCurrent()
                assertEquals(false, foregroundRunning)
                verify(exactly = 2) { scheduled.startScheduledTracking(any(), any()) }
            } finally {
                unmockkStatic("app.logdate.client.location.tracking.ActivityAwareLocationServiceKt")
            }
        }
}
