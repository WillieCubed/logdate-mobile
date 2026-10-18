package app.logdate.client.e2e

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.domain.recommendation.RecallMode
import app.logdate.client.domain.recommendation.WidgetContentType
import app.logdate.client.feature.widgets.PhotoWidgetShape
import app.logdate.client.feature.widgets.WidgetInstanceSettings
import app.logdate.client.location.settings.LocationCaptureMode
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.location.tracking.ActivityAwareLocationService
import app.logdate.client.location.tracking.LocationCaptureStatus
import app.logdate.client.location.tracking.LocationTrackingManager
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class NativeRestoredSettingsE2ETest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val koin get() = GlobalContext.get()
    private val originalTracking get() = File(context.noBackupFilesDir, "native-permission-original-settings.json")
    private val widgetReceipt get() = File(context.noBackupFilesDir, "native-widget-backup-acceptance.json")
    private val widgetId = 1947999571

    private fun requireFixture() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("restorePermissionAcceptance") == "true")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        require(context.packageName.endsWith(".debug"))
    }

    private fun status(value: String) {
        instrumentation.sendStatus(0, Bundle().apply { putString("restorePermission", value) })
    }

    @Test
    fun locationResumesAfterExternalGrants() =
        runBlocking {
            requireFixture()
            require(!originalTracking.exists()) { "Restore the preceding fixture settings first" }
            val settings = koin.get<LocationTrackingSettingsRepository>()
            val original = settings.getSettings()
            FileOutputStream(originalTracking).use { output ->
                output.write(Json.encodeToString(original).encodeToByteArray())
                output.fd.sync()
            }
            val registered = MutableStateFlow(false)
            val locationEvents = CopyOnWriteArrayList<String>()
            val transitionEvents = CopyOnWriteArrayList<String>()
            val registrationSink =
                object : Antilog() {
                    override fun performLog(
                        priority: LogLevel,
                        tag: String?,
                        throwable: Throwable?,
                        message: String?,
                    ) {
                        if (message == "Activity transition updates registered") registered.value = true
                        if (message?.contains("location", ignoreCase = true) == true) locationEvents += message
                        if (message?.contains("transition", ignoreCase = true) == true) transitionEvents += message
                    }
                }
            Napier.base(registrationSink)
            var activity: Activity? = null
            var primaryFailure: Throwable? = null
            try {
                for (permission in listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                )) {
                    assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
                }
                settings.updateSettings(original.copy(backgroundTrackingEnabled = true, captureMode = LocationCaptureMode.ACTIVE))
                assertEquals(true, settings.getSettings().backgroundTrackingEnabled)
                assertEquals(LocationCaptureMode.ACTIVE, settings.getSettings().captureMode)
                withContext(Dispatchers.Main) { koin.get<LocationTrackingManager>().startTracking() }
                delay(1_500)
                assertNull(ActivityAwareLocationService.instance)
                status("RESTORE_LOCATION_GRANT_REQUIRED")
                withTimeout(20_000) {
                    while (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        delay(50)
                    }
                }
                val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                activity = launchResumedActivity(intent)
                val firstStatus =
                    withTimeoutOrNull(90_000) {
                        ActivityAwareLocationService.captureStatus.first {
                            it == LocationCaptureStatus.Running || it == LocationCaptureStatus.Failed
                        }
                    }
                val recentLocationEvents = locationEvents.takeLast(12)
                val statusDetails =
                    "Location recovery did not start: service=${ActivityAwareLocationService.instance != null}, " +
                        "current=${ActivityAwareLocationService.captureStatus.value}, events=$recentLocationEvents"
                assertEquals(LocationCaptureStatus.Running, firstStatus, statusDetails)
                val service = assertNotNull(ActivityAwareLocationService.instance)
                val transitions =
                    ActivityAwareLocationService::class.java.getDeclaredField("activityTransitionPendingIntent").apply {
                        isAccessible =
                            true
                    }
                assertNull(transitions.get(service))
                instrumentation.runOnMainSync { check(activity?.moveTaskToBack(true) == true) }
                awaitActivityStage(requireNotNull(activity), Stage.STOPPED)
                val appRemainsResumed =
                    withContext(Dispatchers.Main) {
                        ActivityLifecycleMonitorRegistry
                            .getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .any { it.packageName == context.packageName }
                    }
                assertEquals(false, appRemainsResumed, "LogDate must leave the foreground before the motion grant")
                status("RESTORE_MOTION_GRANT_REQUIRED")
                withTimeout(20_000) {
                    while (context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        delay(50)
                    }
                }
                status("RESTORE_MOTION_PERMISSION_GRANTED")
                status("RESTORE_ACTIVITY_RESUMING")
                activity = launchResumedActivity(intent)
                status("RESTORE_ACTIVITY_RESUMED_AFTER_MOTION_GRANT")
                val pendingIntentCreated =
                    withTimeoutOrNull(20_000) {
                        while (withContext(Dispatchers.Main) { transitions.get(service) } == null) delay(50)
                        true
                    }
                assertEquals(
                    true,
                    pendingIntentCreated,
                    "The resumed activity did not cause the running service to create a transition request; " +
                        "events=$transitionEvents",
                )
                status("RESTORE_TRANSITION_REQUEST_CREATED")
                val registrationAccepted =
                    withTimeoutOrNull(20_000) {
                        registered.first { it }
                        true
                    }
                assertEquals(
                    true,
                    registrationAccepted,
                    "Play Services did not accept the activity transition request; events=$transitionEvents",
                )
                assertSame(service, ActivityAwareLocationService.instance)
                assertEquals(LocationCaptureStatus.Running, ActivityAwareLocationService.captureStatus.value)
            } catch (error: Throwable) {
                primaryFailure = error
                throw error
            } finally {
                Napier.takeLogarithm(registrationSink)
                try {
                    withContext(NonCancellable) {
                        instrumentation.runOnMainSync { activity?.finish() }
                        restoreTracking()
                    }
                } catch (cleanup: Throwable) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanup) else throw cleanup
                }
            }
        }

    private suspend fun launchResumedActivity(intent: Intent): Activity {
        val foregroundIntent =
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        instrumentation.runOnMainSync { context.startActivity(foregroundIntent) }
        return withTimeout(20_000) {
            var resumed: Activity? = null
            while (resumed == null) {
                resumed =
                    withContext(Dispatchers.Main) {
                        ActivityLifecycleMonitorRegistry
                            .getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .firstOrNull { it.packageName == context.packageName && !it.isFinishing }
                    }
                if (resumed == null) delay(50)
            }
            resumed
        }
    }

    private suspend fun awaitActivityStage(
        activity: Activity,
        stage: Stage,
    ) {
        withTimeout(20_000) {
            while (withContext(Dispatchers.Main) {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).any { it === activity }
                } == false
            ) {
                delay(50)
            }
        }
    }

    @Test
    fun restoreOriginalTrackingSettings() =
        runBlocking {
            requireFixture()
            restoreTracking()
        }

    private suspend fun restoreTracking() {
        if (!originalTracking.isFile) return
        val original = Json.decodeFromString<LocationTrackingSettings>(originalTracking.readText())
        val settings = koin.get<LocationTrackingSettingsRepository>()
        settings.updateSettings(original)
        assertEquals(original, settings.getSettings())
        withContext(Dispatchers.Main) { koin.get<LocationTrackingManager>().startTracking() }
        if (!original.backgroundTrackingEnabled) {
            withTimeout(10_000) { ActivityAwareLocationService.captureStatus.first { it == LocationCaptureStatus.Stopped } }
        }
        check(originalTracking.delete())
    }

    @Test
    fun seedWidgetBackupSettings() =
        runBlocking {
            requireFixture()
            val preferences = context.getSharedPreferences("logdate_widget_instances", Context.MODE_PRIVATE)
            require(preferences.all.keys.none { it.startsWith("$widgetId.") })
            require(!widgetReceipt.exists())
            val note =
                koin
                    .get<LogDateDatabase>()
                    .imageNoteDao()
                    .getAll()
                    .first()
            val settings = WidgetInstanceSettings(context)
            settings.saveRecall(widgetId, RecallMode.REDISCOVER, setOf(WidgetContentType.AUDIO))
            settings.saveChosenNote(widgetId, note.uid.toString())
            settings.saveUsePhotoPrompt(widgetId, false)
            settings.savePhotoShape(widgetId, PhotoWidgetShape.CIRCLE)
            check(preferences.edit().commit())
            widgetReceipt.writeText(JSONObject().put("id", widgetId).put("note", note.uid.toString()).toString())
            assertEquals(note.uid.toString(), settings.chosenNoteId(widgetId))
        }

    @Test
    fun cleanupWidgetBackupSettings() {
        requireFixture()
        if (!widgetReceipt.exists()) return
        require(JSONObject(widgetReceipt.readText()).getInt("id") == widgetId)
        WidgetInstanceSettings(context).remove(widgetId)
        check(context.getSharedPreferences("logdate_widget_instances", Context.MODE_PRIVATE).edit().commit())
        check(widgetReceipt.delete())
    }
}
