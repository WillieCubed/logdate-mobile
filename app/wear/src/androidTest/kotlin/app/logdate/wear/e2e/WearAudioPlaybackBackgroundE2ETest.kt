package app.logdate.wear.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackService
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class WearAudioPlaybackBackgroundE2ETest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var playbackManager: AndroidAudioPlaybackManager
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    @Before
    fun setUp() {
        grantNotificationPermissionIfNeeded()
        LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.AUDIO_PLAYBACK)
        playbackManager = AndroidAudioPlaybackManager(context, scope)
    }

    @After
    fun tearDown() {
        controller?.let { mediaController ->
            runOnMainSync { mediaController.release() }
        }
        controllerFuture?.cancel(true)
        runOnMainSync { playbackManager.release() }
        notificationManager.cancel(AudioPlaybackService.NOTIFICATION_ID)
        context.stopService(Intent(context, AudioPlaybackService::class.java))
        scope.cancel()
    }

    @Test
    fun playbackNotificationAndSessionStaySynchronizedAfterWearHome() {
        val audioFile = silentWavFile(durationSeconds = 60)

        runOnMainSync {
            playbackManager.startPlayback(
                uri = Uri.fromFile(audioFile).toString(),
                metadata =
                    AudioPlaybackMetadata(
                        title = "Wear background playback check",
                        subtitle = "Audio note",
                        noteId = Uuid.random(),
                        journalNames = listOf("Audit"),
                    ),
                onProgressUpdated = {},
                onPlaybackCompleted = {},
            )
        }

        waitUntil("Wear playback starts or reports unsuitable output") {
            val status = playbackManager.playbackStatus.value
            status.isPlaying || status.isSuppressedForUnsuitableOutput
        }
        assumeFalse(
            "Wear managed device does not expose suitable audio output for background playback validation",
            playbackManager.playbackStatus.value.isSuppressedForUnsuitableOutput,
        )

        waitUntil("Wear media notification appears") { activePlaybackNotification() != null }
        val notification = checkNotNull(activePlaybackNotification()?.notification)
        assertEquals(AudioPlaybackService.CHANNEL_ID, notification.channelId)
        assertEquals(
            "Wear background playback check",
            notification.extras.getCharSequence("android.title")?.toString(),
        )

        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        waitUntil("Wear playback remains active after Home") {
            playbackManager.playbackStatus.value.isPlaying
        }
        waitUntil("Wear notification remains active after Home") {
            activePlaybackNotification() != null
        }

        sendNotificationAction(titleContains = "Pause")
        waitUntil("Wear notification pause action syncs to playback state") {
            !playbackManager.playbackStatus.value.isPlaying
        }

        sendNotificationAction(titleContains = "Play")
        waitUntil("Wear notification play action syncs to playback state") {
            playbackManager.playbackStatus.value.isPlaying
        }

        val mediaController = mediaController()
        runOnMainSync { mediaController.pause() }
        waitUntil("Wear MediaSession pause syncs to playback state") {
            !playbackManager.playbackStatus.value.isPlaying
        }

        runOnMainSync { mediaController.play() }
        waitUntil("Wear MediaSession play syncs to playback state") {
            playbackManager.playbackStatus.value.isPlaying
        }

        assertTrue(
            "Wear playback should retain progress after Home and media actions",
            playbackManager.playbackStatus.value.progress > 0f,
        )
    }

    @Test
    fun wearApplicationRegistersPlaybackNotificationChannel() {
        val channel = notificationManager.getNotificationChannel(AudioPlaybackService.CHANNEL_ID)

        assertNotNull("Wear app must register the audio playback notification channel", channel)
        assertEquals(AudioPlaybackService.CHANNEL_ID, channel.id)
    }

    private fun activePlaybackNotification() =
        notificationManager
            .activeNotifications
            .firstOrNull { it.id == AudioPlaybackService.NOTIFICATION_ID }

    private fun sendNotificationAction(titleContains: String) {
        val action =
            activePlaybackNotification()
                ?.notification
                ?.actions
                ?.firstOrNull { action ->
                    action.title?.toString()?.contains(titleContains, ignoreCase = true) == true
                }
                ?: error(
                    "No Wear media notification action containing $titleContains. " +
                        "Available actions: ${availableNotificationActionTitles()}",
                )

        sendPendingIntent(action)
    }

    private fun availableNotificationActionTitles(): String =
        activePlaybackNotification()
            ?.notification
            ?.actions
            ?.joinToString { action -> action.title?.toString().orEmpty() }
            .orEmpty()

    private fun sendPendingIntent(action: Notification.Action) {
        try {
            action.actionIntent.send()
        } catch (exception: PendingIntent.CanceledException) {
            error("Wear notification action ${action.title} was canceled: ${exception.message}")
        }
    }

    private fun mediaController(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        return future.get(10, TimeUnit.SECONDS).also { controller = it }
    }

    private fun grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun waitUntil(
        description: String,
        timeoutMs: Long = 15_000,
        condition: () -> Boolean,
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Timed out waiting for $description")
    }

    private fun runOnMainSync(block: () -> Unit) {
        getInstrumentation().runOnMainSync(block)
    }

    private fun silentWavFile(durationSeconds: Int): File {
        val sampleRate = 8_000
        val channelCount = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channelCount * bitsPerSample / 8
        val dataSize = durationSeconds * byteRate
        val file = File(context.cacheDir, "wear-background-playback-check.wav")

        file.outputStream().use { output ->
            output.write("RIFF".toByteArray())
            output.writeIntLE(36 + dataSize)
            output.write("WAVE".toByteArray())
            output.write("fmt ".toByteArray())
            output.writeIntLE(16)
            output.writeShortLE(1)
            output.writeShortLE(channelCount)
            output.writeIntLE(sampleRate)
            output.writeIntLE(byteRate)
            output.writeShortLE(channelCount * bitsPerSample / 8)
            output.writeShortLE(bitsPerSample)
            output.write("data".toByteArray())
            output.writeIntLE(dataSize)
            output.write(ByteArray(dataSize))
        }

        return file
    }

    private fun java.io.OutputStream.writeIntLE(value: Int) {
        write(
            ByteBuffer
                .allocate(Int.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(value)
                .array(),
        )
    }

    private fun java.io.OutputStream.writeShortLE(value: Int) {
        write(
            ByteBuffer
                .allocate(Short.SIZE_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(value.toShort())
                .array(),
        )
    }
}
