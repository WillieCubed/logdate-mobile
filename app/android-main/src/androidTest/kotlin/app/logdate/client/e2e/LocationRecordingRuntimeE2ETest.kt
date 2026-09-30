package app.logdate.client.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.ActivityAwareLocationService
import app.logdate.client.location.tracking.LocationCaptureStatus
import app.logdate.client.location.tracking.LocationRecordingActionReceiver
import app.logdate.client.location.tracking.LocationTrackingManager
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.repository.location.LocationCapturePipeline
import app.logdate.client.repository.location.LocationCaptureSource
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

/** Real fused subscriptions, notification actions and Room evidence, exclusively on managed emulators. */
@RunWith(AndroidJUnit4::class)
class LocationRecordingRuntimeE2ETest {
    @Test
    fun `complete day captures evidence and its real notification pauses and resumes recording`() =
        runBlocking<Unit> {
            requireManagedEmulator()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val koin = GlobalContext.get()
            val settings = koin.get<LocationTrackingSettingsRepository>()
            val manager = koin.get<LocationTrackingManager>()
            val history = koin.get<LocationHistoryRepository>()
            val originalSettings = settings.getSettings()
            val originalMockMode = mockAppOpMode(context)
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val observedAtMillis = System.currentTimeMillis() - 5_000
            val sample =
                Location("fused").apply {
                    latitude = 36.167421
                    longitude = -115.148732
                    accuracy = 4.5f
                    speed = 2.0f
                    bearing = 90f
                    time = observedAtMillis
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - 5_000_000_000L
                }
            var mockEnabled = false
            var scenario: ActivityScenario<ComponentActivity>? = null
            try {
                grantPermissions(context)
                LogDateNotificationRegistrar(context).registerAllPhoneChannels()
                settings.setBackgroundTrackingEnabled(false)
                manager.stopTracking()
                awaitStatus(LocationCaptureStatus.Stopped)
                shell("appops set ${context.packageName} android:mock_location allow")
                Tasks.await(fused.setMockMode(true), 15, TimeUnit.SECONDS)
                mockEnabled = true
                scenario = ActivityScenario.launch(ComponentActivity::class.java)
                settings.updateSettings(originalSettings.copy(backgroundTrackingEnabled = true, captureMode = LocationCaptureMode.ACTIVE))
                instrumentation.runOnMainSync { manager.startTracking() }
                awaitStatus(LocationCaptureStatus.Running)
                val running = awaitNotificationAction(context, "Pause")
                assertTrue(running.first.flags and Notification.FLAG_ONGOING_EVENT != 0)
                Tasks.await(fused.setMockLocation(sample), 15, TimeUnit.SECONDS)
                Tasks.await(fused.flushLocations(), 15, TimeUnit.SECONDS)
                val recorded = awaitSample(history, observedAtMillis)
                assertEquals(Instant.fromEpochMilliseconds(observedAtMillis), recorded.timestamp)
                assertTrue(recorded.loggedAt > recorded.timestamp)
                assertEquals(sample.latitude, recorded.location.latitude, 0.0000001)
                assertEquals(sample.longitude, recorded.location.longitude, 0.0000001)
                assertEquals(4.5f, recorded.accuracyMeters)
                assertEquals(2.0f, recorded.speedMetersPerSecond)
                assertEquals(90f, recorded.bearingDegrees)
                assertTrue(recorded.isMock)
                assertEquals(LocationCapturePipeline.HIGH_DETAIL, recorded.capturePipeline)
                assertEquals(LocationCaptureSource.FOREGROUND_STREAM, recorded.captureSource)

                running.second.actionIntent.send()
                awaitStatus(LocationCaptureStatus.Stopped)
                assertFalse(settings.getSettings().backgroundTrackingEnabled)
                assertEquals(LocationCaptureMode.ACTIVE, settings.getSettings().captureMode)
                awaitNotificationAction(context, "Resume").second.actionIntent.send()
                awaitStatus(LocationCaptureStatus.Running)
                assertTrue(settings.getSettings().backgroundTrackingEnabled)
                assertEquals(LocationCaptureMode.ACTIVE, settings.getSettings().captureMode)
                awaitNotificationAction(context, "Pause")
            } finally {
                settings.setBackgroundTrackingEnabled(false)
                manager.stopTracking()
                awaitStatus(LocationCaptureStatus.Stopped)
                if (mockEnabled) Tasks.await(fused.setMockMode(false), 15, TimeUnit.SECONDS)
                shell("appops set ${context.packageName} android:mock_location $originalMockMode")
                history.getAllLocationHistory().filter { it.timestamp.toEpochMilliseconds() == observedAtMillis && it.isMock }.forEach {
                    history.deleteLocationEntry(it.userId, it.deviceId, it.timestamp).getOrThrow()
                }
                LocationRecordingActionReceiver.clearPausedNotification(context)
                settings.updateSettings(originalSettings)
                if (originalSettings.backgroundTrackingEnabled) manager.startTracking()
                scenario?.close()
            }
        }

    private fun requireManagedEmulator() {
        val emulatorHardware = Build.HARDWARE in setOf("ranchu", "goldfish", "cutf_cvm")
        val emulatorImage = Build.FINGERPRINT.contains("generic", ignoreCase = true) || Build.FINGERPRINT.contains("sdk_gphone")
        check(emulatorHardware && emulatorImage) { "This test must run only on a Gradle Managed Device emulator." }
    }

    private fun grantPermissions(context: Context) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ).forEach { automation.grantRuntimePermission(context.packageName, it) }
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private suspend fun awaitStatus(status: LocationCaptureStatus) =
        withTimeout(30_000) {
            ActivityAwareLocationService.captureStatus.first { it == status }
        }

    private suspend fun awaitNotificationAction(
        context: Context,
        title: String,
    ): Pair<Notification, Notification.Action> =
        withTimeout(20_000) {
            val notifications = context.getSystemService(NotificationManager::class.java)
            var found: Pair<Notification, Notification.Action>? = null
            while (found == null) {
                found =
                    notifications.activeNotifications
                        .asSequence()
                        .map { it.notification }
                        .filter { it.channelId == LogDateNotificationChannelKey.LOCATION_HISTORY.id }
                        .mapNotNull { notification ->
                            notification.actions?.firstOrNull { it.title.toString() == title }?.let { notification to it }
                        }.firstOrNull()
                if (found == null) delay(100)
            }
            found
        }

    private suspend fun awaitSample(
        history: LocationHistoryRepository,
        observedAtMillis: Long,
    ): LocationHistoryItem =
        withTimeout(75_000) {
            var found: LocationHistoryItem? = null
            while (found == null) {
                found =
                    history.getAllLocationHistory().firstOrNull {
                        it.timestamp.toEpochMilliseconds() == observedAtMillis &&
                            it.captureSource == LocationCaptureSource.FOREGROUND_STREAM
                    }
                if (found == null) delay(200)
            }
            found
        }

    private fun mockAppOpMode(context: Context): String =
        Regex("(?:MOCK_LOCATION|android:mock_location): (allow|ignore|deny|default|foreground)")
            .find(shell("appops get ${context.packageName} android:mock_location"))
            ?.groupValues
            ?.get(1) ?: "default"

    private fun shell(command: String): String {
        requireManagedEmulator()
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }
}
