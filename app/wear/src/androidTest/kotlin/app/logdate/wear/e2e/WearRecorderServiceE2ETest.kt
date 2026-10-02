package app.logdate.wear.e2e

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.audio.AndroidAudioDurationResolver
import app.logdate.client.media.audio.AndroidAudioStorage
import app.logdate.client.media.audio.AndroidRecordingServiceController
import app.logdate.client.media.audio.RecordingSessionOptions
import app.logdate.client.media.device.AndroidAudioRouteRepository
import app.logdate.wear.data.storage.StorageSpaceChecker
import app.logdate.wear.presentation.MainActivity
import app.logdate.wear.recording.WearAudioRecordingManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Records through the real foreground service on a Wear OS device, the path the unit tests fake.
 *
 * These prove the pieces the unit tests cannot: that the shared recording service starts as a
 * microphone foreground service on Wear OS, that the notification channel it posts to exists, and
 * that stop returns only once the file can be played. They record a couple of seconds of whatever
 * the device's microphone hears, which is silence on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class WearRecorderServiceE2ETest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var manager: WearAudioRecordingManager
    private lateinit var activity: ActivityScenario<MainActivity>
    private val recordedFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        // A microphone foreground service may only start while the app is on screen, as it is when
        // someone presses the record button.
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
                            ),
                    ),
                microphonePermission = { true },
            )
    }

    @After
    fun tearDown() =
        runBlocking {
            if (manager.isRecording.value) manager.discard()
            recordedFiles.forEach { it.delete() }
            activity.close()
        }

    @Test
    fun recording_produces_a_playable_file_of_about_the_recorded_length() =
        runBlocking {
            assertTrue("The recorder should confirm it is running", manager.start())
            assertTrue(manager.isRecording.value)

            delay(2_500)
            val path = manager.stop()

            assertNotNull("Stopping should return the finalized file", path)
            val file = File(path!!).also { recordedFiles += it }
            assertTrue("The file should hold audio", file.length() > 0)
            val durationMs = AndroidAudioDurationResolver(context).resolveDurationMs(path)
            assertNotNull("The file should be readable as audio", durationMs)
            assertTrue("Expected about 2.5 s but was $durationMs ms", durationMs!! in 1_500L..4_500L)
            assertFalse(manager.isRecording.value)
        }

    @Test
    fun time_spent_paused_is_not_recorded() =
        runBlocking {
            assertTrue(manager.start())
            delay(1_500)
            assertTrue("The recorder should pause", manager.pause())
            assertTrue(manager.isPaused.value)
            delay(2_500)
            assertTrue("The recorder should resume", manager.resume())
            delay(1_500)

            val path = manager.stop()

            val file = File(path!!).also { recordedFiles += it }
            val durationMs = AndroidAudioDurationResolver(context).resolveDurationMs(file.absolutePath)
            assertNotNull(durationMs)
            assertTrue("Expected about 3 s of the 5.5 s session but was $durationMs ms", durationMs!! in 2_000L..4_500L)
        }

    @Test
    fun discarding_removes_the_recording() =
        runBlocking {
            val audioDirectory = File(context.filesDir, "audio_notes")
            val before = audioDirectory.listFiles().orEmpty().map { it.name }.toSet()
            assertTrue(manager.start())
            delay(1_000)

            manager.discard()

            assertFalse(manager.isRecording.value)
            val after = audioDirectory.listFiles().orEmpty().map { it.name }.toSet()
            assertEquals("A discarded recording should leave no file behind", before, after)
        }

    @Test
    fun a_second_session_can_follow_the_first() =
        runBlocking {
            assertTrue(manager.start())
            delay(1_000)
            recordedFiles += File(requireNotNull(manager.stop()))

            assertTrue("The recorder should be reusable", manager.start())
            delay(1_000)
            val second = manager.stop()

            assertNotNull(second)
            recordedFiles += File(second!!)
            assertEquals(2, recordedFiles.distinct().size)
        }

    @Test
    fun stopping_when_nothing_is_recording_returns_nothing() =
        runBlocking {
            assertNull(manager.stop())
        }
}
