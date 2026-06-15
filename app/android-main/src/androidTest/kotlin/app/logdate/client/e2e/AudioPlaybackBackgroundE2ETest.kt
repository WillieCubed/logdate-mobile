package app.logdate.client.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.logdate.client.media.audio.AndroidAudioPlaybackManager
import app.logdate.client.media.audio.AudioPlaybackMetadata
import app.logdate.client.media.audio.AudioPlaybackService
import app.logdate.client.notifications.LogDateNotificationRegistrar
import com.google.common.util.concurrent.ListenableFuture
import io.github.aakira.napier.Napier
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class AudioPlaybackBackgroundE2ETest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var playbackManager: AndroidAudioPlaybackManager
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    @Before
    fun setUp() {
        LogDateNotificationRegistrar(context).registerAllPhoneChannels()
        grantNotificationPermissionIfNeeded()
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
    fun playbackNotificationAndSessionStaySynchronizedAfterBackgroundAndLock() {
        val audioFile = silentWavFile(durationSeconds = 90)
        val noteId = Uuid.random()

        runOnMainSync {
            playbackManager.startPlayback(
                uri = Uri.fromFile(audioFile).toString(),
                metadata =
                    AudioPlaybackMetadata(
                        title = "Background playback check",
                        subtitle = "Audio note",
                        noteId = noteId,
                        journalNames = listOf("Audit"),
                    ),
                onProgressUpdated = {},
                onPlaybackCompleted = {},
            )
        }

        waitUntil("playback starts or reports unsuitable output") {
            val status = playbackManager.playbackStatus.value
            status.isPlaying || status.isSuppressedForUnsuitableOutput
        }
        assertTrue(
            "Managed device must have suitable audio output for background playback validation",
            !playbackManager.playbackStatus.value.isSuppressedForUnsuitableOutput,
        )
        waitUntil("media notification appears") { activePlaybackNotification() != null }

        val notification = activePlaybackNotification()
        assertEquals(AudioPlaybackService.CHANNEL_ID, notification?.notification?.channelId)
        assertEquals(
            "Background playback check",
            notification?.notification?.extras?.getCharSequence("android.title")?.toString(),
        )

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        waitUntil("playback remains active after home") { playbackManager.playbackStatus.value.isPlaying }
        waitUntil("notification remains active after home") { activePlaybackNotification() != null }

        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitUntil("playback remains active behind settings") { playbackManager.playbackStatus.value.isPlaying }
        waitUntil("notification remains active behind settings") { activePlaybackNotification() != null }

        context.startActivity(
            checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)) {
                "LogDate launch intent is required for refocus validation"
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        waitUntil("playback remains active after app refocus") {
            playbackManager.playbackStatus.value.isPlaying
        }
        waitUntil("notification remains active after app refocus") { activePlaybackNotification() != null }

        device.sleep()
        Thread.sleep(65.seconds.inWholeMilliseconds)
        waitUntil("notification remains active while locked") { activePlaybackNotification() != null }
        device.wakeUp()
        waitUntil("playback remains active after wake") { playbackManager.playbackStatus.value.isPlaying }
        waitUntil("notification remains active after wake") { activePlaybackNotification() != null }

        if (!tryTapVisibleMediaControls(device)) {
            waitUntil("notification pause action becomes available after wake") {
                availableNotificationActionTitles().contains("Pause", ignoreCase = true)
            }
            sendNotificationAction(titleContains = "Pause")
            waitUntil("notification pause action syncs to playback state") {
                !playbackManager.playbackStatus.value.isPlaying
            }
            waitUntil("notification play action becomes available after pause") {
                availableNotificationActionTitles().contains("Play", ignoreCase = true) ||
                    availableNotificationActionTitles().contains("Resume", ignoreCase = true)
            }

            sendNotificationAction(titleContains = "Play")
            waitUntil("notification play action syncs to playback state") {
                playbackManager.playbackStatus.value.isPlaying
            }
        }

        val mediaController = mediaController()
        runOnMainSync { mediaController.pause() }
        waitUntil("media session pause syncs to playback state") {
            !playbackManager.playbackStatus.value.isPlaying
        }

        runOnMainSync { mediaController.play() }
        waitUntil("media session play syncs to playback state") {
            playbackManager.playbackStatus.value.isPlaying
        }

        assertTrue(
            "Playback should retain progress after background/lock refocus",
            playbackManager.playbackStatus.value.progress > 0f,
        )
    }

    @Test
    fun playbackContinuesWhenAppIsVisibleButNotFocusedInMultiWindow() {
        ActivityScenario.launch(VideoPlaybackHostActivity::class.java).use { scenario ->
            val audioFile = silentWavFile(durationSeconds = 30)

            runOnMainSync {
                playbackManager.startPlayback(
                    uri = Uri.fromFile(audioFile).toString(),
                    metadata =
                        AudioPlaybackMetadata(
                            title = "Multi-window playback check",
                            subtitle = "Audio note",
                            noteId = Uuid.random(),
                            journalNames = listOf("Audit"),
                        ),
                    onProgressUpdated = {},
                    onPlaybackCompleted = {},
                )
            }

            waitUntil("playback starts for multi-window validation") {
                playbackManager.playbackStatus.value.isPlaying ||
                    playbackManager.playbackStatus.value.isSuppressedForUnsuitableOutput
            }
            assertTrue(
                "Managed device must have suitable audio output for multi-window playback validation",
                !playbackManager.playbackStatus.value.isSuppressedForUnsuitableOutput,
            )

            scenario.onActivity { activity ->
                activity.startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
                )
            }

            if (!waitForCondition("host activity enters multi-window mode") {
                    var isInMultiWindow = false
                    scenario.onActivity { activity ->
                        isInMultiWindow = activity.isInMultiWindowMode
                    }
                    isInMultiWindow
                }
            ) {
                return@use
            }
            waitUntil("playback remains active while app is visible but not focused") {
                playbackManager.playbackStatus.value.isPlaying
            }
            waitUntil("media notification remains active in multi-window") {
                activePlaybackNotification() != null
            }
        }
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
                    "No media notification action containing $titleContains. " +
                        "Available actions: ${availableNotificationActionTitles()}",
                )

        sendPendingIntent(action)
    }

    private fun tryTapVisibleMediaControls(device: UiDevice): Boolean {
        expandNotificationShade(device)

        val pause =
            visibleMediaControl(device = device, titleContains = "Pause") ?: return false
        assertTrue(
            "Visible media control containing Pause should be clickable",
            pause.isClickable,
        )
        pause.click()
        waitUntil("visible pause control syncs to playback state") {
            !playbackManager.playbackStatus.value.isPlaying
        }

        val play =
            visibleMediaControl(device = device, titleContains = "Play")
                ?: visibleMediaControl(device = device, titleContains = "Resume")
                ?: return false
        assertTrue(
            "Visible media control containing Play or Resume should be clickable",
            play.isClickable,
        )
        play.click()
        waitUntil("visible play control syncs to playback state") {
            playbackManager.playbackStatus.value.isPlaying
        }
        return true
    }

    private fun visibleMediaControl(
        device: UiDevice,
        titleContains: String,
    ) = device.wait(Until.findObject(By.descContains(titleContains)), 5_000)
        ?: device.wait(Until.findObject(By.textContains(titleContains)), 5_000)

    private fun expandNotificationShade(device: UiDevice) {
        device.openNotification()
        runCatching {
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("cmd statusbar expand-notifications")
                .use { }
        }.onFailure { exception ->
            Napier.e(exception) { "Failed to expand notification shade with shell command" }
        }
        device.waitForIdle()
    }

    private fun dumpHierarchyAndError(
        device: UiDevice,
        label: String,
        message: String,
    ): Nothing {
        val hierarchyFile = File(context.cacheDir, "audio-playback-$label-hierarchy.xml")
        runCatching {
            device.dumpWindowHierarchy(hierarchyFile)
        }.onFailure { exception ->
            Napier.e(exception) { "Failed to dump $label hierarchy to ${hierarchyFile.absolutePath}" }
        }
        val hierarchyExcerpt =
            runCatching {
                hierarchyFile
                    .readText()
                    .lineSequence()
                    .filter { line ->
                        line.contains("clickable=\"true\"") ||
                            line.contains("action", ignoreCase = true) ||
                            line.contains("control", ignoreCase = true) ||
                            line.contains("button", ignoreCase = true) ||
                            line.contains("Play", ignoreCase = true) ||
                            line.contains("Resume", ignoreCase = true) ||
                            line.contains("Pause", ignoreCase = true) ||
                            line.contains("media", ignoreCase = true) ||
                            line.contains("notification", ignoreCase = true)
                    }.take(140)
                    .joinToString(separator = "\n")
            }.getOrElse { readException ->
                Napier.e(readException) { "Failed to read hierarchy dump from ${hierarchyFile.absolutePath}" }
                "<unavailable>"
            }
        error(
            buildString {
                append(message)
                append(". Hierarchy dump: ")
                append(hierarchyFile.absolutePath)
                append("\n")
                append(hierarchyExcerpt)
            },
        )
    }

    private fun sendPendingIntent(action: Notification.Action) {
        try {
            action.actionIntent.send()
        } catch (exception: PendingIntent.CanceledException) {
            error("Notification action ${action.title} was canceled: ${exception.message}")
        }
    }

    private fun availableNotificationActionTitles(): String =
        activePlaybackNotification()
            ?.notification
            ?.actions
            ?.joinToString { action -> action.title?.toString().orEmpty() }
            .orEmpty()

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
        if (!waitForCondition(description, timeoutMs, condition)) {
            error("Timed out waiting for $description")
        }
    }

    private fun waitForCondition(
        description: String,
        timeoutMs: Long = 15_000,
        condition: () -> Boolean,
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return false
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
        val file = File(context.cacheDir, "background-playback-check.wav")

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
