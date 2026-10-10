package app.logdate.wear.e2e

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AndroidRecordingServiceController
import app.logdate.client.media.audio.AudioRecordingService
import app.logdate.client.media.audio.CrashSafeRecording
import app.logdate.client.media.audio.RecordingSessionOptions
import app.logdate.client.media.device.AndroidAudioRouteRepository
import app.logdate.wear.LogDateWearApplication
import app.logdate.wear.R
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.recording.WearAudioRecordingManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Records through the real foreground service on a Wear OS emulator and reads the Ongoing Activity
 * back from the posted notification, the way the watch face finds it.
 */
@RunWith(AndroidJUnit4::class)
class OngoingRecordingE2ETest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var activity: ActivityScenario<MainActivity>
    private lateinit var manager: WearAudioRecordingManager
    private val recordedFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        runBlocking { setNewWatchApp(enabled = true) }
        // A microphone foreground service may only start while the app is on screen.
        activity = ActivityScenario.launch(MainActivity::class.java)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS).forEach { permission ->
            automation.grantRuntimePermission(context.packageName, permission)
        }
        manager =
            WearAudioRecordingManager(
                storageChecker = StorageSpaceChecker(context),
                audioStorage = AndroidAudioStorage(context),
                audioRouteRepository = AndroidAudioRouteRepository(context),
                serviceController =
                    AndroidRecordingServiceController(
                        context = context,
                        options =
                            RecordingSessionOptions(
                                maxDurationMs = WearAudioRecordingManager.MAX_RECORDING_DURATION.inWholeMilliseconds,
                                pauseOnInterruption = true,
                                holdWakeLock = true,
                                crashSafe = true,
                            ),
                    ),
                microphonePermission = { true },
            )
    }

    @After
    fun tearDown() =
        runBlocking {
            if (manager.isRecording.value) manager.discard()
            recordedFiles.forEach { file ->
                file.delete()
                CrashSafeRecording.inFlightFile(file).delete()
            }
            activity.close()
            setNewWatchApp(enabled = false)
        }

    @Test
    fun a_recording_shows_on_the_watch_face_until_it_stops() =
        runBlocking {
            assertTrue(manager.start())

            val ongoing = awaitOngoingActivity { true }
            assertEquals(AudioRecordingService.NOTIFICATION_ID, ongoing.notificationId)
            assertEquals(context.getString(R.string.wear_ongoing_recording_title), ongoing.title.toString())
            assertNotNull(ongoing.touchIntent)

            manager.stop()?.let { path -> recordedFiles += File(path) }
            awaitNoOngoingActivity()
        }

    @Test
    fun a_paused_recording_stops_its_stopwatch() =
        runBlocking<Unit> {
            assertTrue(manager.start())
            delay(1_500)

            assertTrue(manager.pause())
            awaitOngoingActivity { ongoing -> ongoing.stopwatch().isPaused }
            assertTrue(manager.resume())
            awaitOngoingActivity { ongoing -> !ongoing.stopwatch().isPaused }
        }

    @Test
    fun the_current_watch_app_records_without_an_ongoing_activity() =
        runBlocking {
            setNewWatchApp(enabled = false)

            assertTrue(manager.start())
            delay(1_500)

            assertNull(OngoingActivity.recoverOngoingActivity(context))
        }

    private suspend fun awaitOngoingActivity(matches: (OngoingActivity) -> Boolean): OngoingActivity {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val ongoing = OngoingActivity.recoverOngoingActivity(context)
            if (ongoing != null && matches(ongoing)) return ongoing
            delay(POLL_MS)
        }
        throw AssertionError("No matching Ongoing Activity within $TIMEOUT_MS ms")
    }

    private suspend fun awaitNoOngoingActivity() {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (OngoingActivity.recoverOngoingActivity(context) == null) return
            delay(POLL_MS)
        }
        throw AssertionError("The Ongoing Activity was still posted after the recording stopped")
    }

    private fun OngoingActivity.stopwatch(): Status.StopwatchPart {
        val status = assertNotNull(status)
        return status.getPart(status.partNames.single()) as Status.StopwatchPart
    }

    /** Sets the flag and waits until the app's switch, which the notification reads, has seen it. */
    private suspend fun setNewWatchApp(enabled: Boolean) {
        KoinJavaComponent.getKoin().get<FeatureFlagStore>().setEnabled(FeatureFlag.WATCH_EXPERIENCE_V2, enabled)
        val app = context as LogDateWearApplication
        withTimeout(TIMEOUT_MS) { app.newWatchApp.state.first { it == enabled } }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 200L
    }
}
