package app.logdate.wear.e2e

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackOptions
import app.logdate.client.media.audio.AudioPlaybackService
import app.logdate.client.media.audio.PlaybackSessionCommands
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import kotlin.uuid.Uuid

/**
 * Drives the real playback session on a Wear OS emulator: it offers skip silence to controllers,
 * refuses the command without a value, and a recording starts and moves where and as fast as asked.
 * A tone is played, because skip silence would skip a silent file to its end. A player the system
 * suppresses for want of an audio output is still prepared and positioned, so these checks hold on a
 * managed device with no speaker.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackSessionCommandsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var playbackManager: AndroidAudioPlaybackManager
    private var controller: MediaController? = null

    @Before
    fun setUp() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.AUDIO_PLAYBACK)
        playbackManager = AndroidAudioPlaybackManager(context, scope)
    }

    @After
    fun tearDown() {
        controller?.let { mediaController -> runOnMainSync { mediaController.release() } }
        runOnMainSync { playbackManager.release() }
        context.stopService(Intent(context, AudioPlaybackService::class.java))
        scope.cancel()
    }

    @Test
    fun the_session_offers_skip_silence_to_controllers() {
        val mediaController = mediaController()
        var available = false

        runOnMainSync { available = mediaController.isSessionCommandAvailable(PlaybackSessionCommands.SET_SKIP_SILENCE) }

        assertTrue(available)
    }

    @Test
    fun skip_silence_without_a_value_is_refused() {
        val mediaController = mediaController()
        lateinit var result: ListenableFuture<SessionResult>

        runOnMainSync { result = mediaController.sendCustomCommand(PlaybackSessionCommands.SET_SKIP_SILENCE, Bundle.EMPTY) }

        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).resultCode)
    }

    @Test
    fun a_recording_starts_where_and_as_fast_as_asked() {
        startTone(AudioPlaybackOptions(startPositionMs = 20_000L, speed = 1.5f, skipSilence = true))

        waitUntil("the session turns skip silence on") { playbackManager.playbackStatus.value.skipSilenceEnabled }
        waitUntil("speed 1.5 is reported") { playbackManager.playbackStatus.value.playbackSpeed == 1.5f }
        waitUntil("the position starts at 0:20") { playbackManager.playbackStatus.value.positionMs >= 20_000L }
    }

    @Test
    fun speed_and_position_change_while_playing() {
        startTone(AudioPlaybackOptions())

        runOnMainSync {
            playbackManager.setPlaybackSpeed(2f)
            playbackManager.seekToPosition(30_000L)
        }

        waitUntil("speed 2 is reported") { playbackManager.playbackStatus.value.playbackSpeed == 2f }
        waitUntil("the position moved to 0:30") { playbackManager.playbackStatus.value.positionMs >= 30_000L }
    }

    @Test
    fun another_controller_pauses_and_resumes_the_reported_playback_intent() {
        startTone(AudioPlaybackOptions())
        val remote = mediaController()
        runOnMainSync { remote.pause() }
        waitUntil("the watch receives the external pause") { !playbackManager.playbackStatus.value.playWhenReady }
        runOnMainSync { remote.play() }
        waitUntil("the watch receives the external resume") { playbackManager.playbackStatus.value.playWhenReady }
    }

    private fun startTone(options: AudioPlaybackOptions) {
        val audio = toneWavFile(durationSeconds = 60)
        runOnMainSync {
            playbackManager.startPlayback(
                uri = Uri.fromFile(audio).toString(),
                metadata = AudioPlaybackMetadata(noteId = Uuid.random()),
                options = options,
                onProgressUpdated = {},
                onPlaybackCompleted = {},
            )
        }
        waitUntil("playback starts or reports an unsuitable output") {
            val status = playbackManager.playbackStatus.value
            status.duration.inWholeMilliseconds > 0L &&
                (status.isPlaying || status.isSuppressedForUnsuitableOutput)
        }
    }

    private fun mediaController(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        return MediaController
            .Builder(context, token)
            .buildAsync()
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .also { controller = it }
    }

    private fun runOnMainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun waitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < TIMEOUT_SECONDS * 1_000) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Timed out waiting until $description")
    }

    private fun toneWavFile(durationSeconds: Int): File {
        val sampleRate = 8_000
        val dataSize = durationSeconds * sampleRate * 2
        val file = File(context.cacheDir, "playback-session-tone.wav")
        file.outputStream().use { output ->
            output.write("RIFF".toByteArray())
            output.writeIntLe(36 + dataSize)
            output.write("WAVE".toByteArray())
            output.write("fmt ".toByteArray())
            output.writeIntLe(16)
            output.writeShortLe(1)
            output.writeShortLe(1)
            output.writeIntLe(sampleRate)
            output.writeIntLe(sampleRate * 2)
            output.writeShortLe(2)
            output.writeShortLe(16)
            output.write("data".toByteArray())
            output.writeIntLe(dataSize)
            val samples = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            repeat(durationSeconds * sampleRate) { index ->
                samples.putShort((sin(2 * PI * TONE_HZ * index / sampleRate) * AMPLITUDE).toInt().toShort())
            }
            output.write(samples.array())
        }
        return file
    }

    private fun OutputStream.writeIntLe(value: Int) {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putInt(value)
        write(bytes.array())
    }

    private fun OutputStream.writeShortLe(value: Int) {
        val bytes = ByteBuffer.allocate(Short.SIZE_BYTES)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        bytes.putShort(value.toShort())
        write(bytes.array())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val TONE_HZ = 440.0
        const val AMPLITUDE = 8_000.0
    }
}
