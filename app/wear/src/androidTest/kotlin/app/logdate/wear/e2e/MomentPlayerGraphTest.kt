package app.logdate.wear.e2e

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.audio.AudioPlaybackService
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.playback.SyncedAudioWatchMediaCache
import app.logdate.wear.playback.WatchMediaCache
import app.logdate.wear.v2.playback.MomentPlayer
import app.logdate.wear.v2.playback.PlaybackFailure
import app.logdate.wear.v2.playback.PlaybackPreferencesStore
import app.logdate.wear.v2.playback.PlaybackSpeed
import app.logdate.wear.v2.playback.PlaybackState
import app.logdate.wear.v2.playback.SharedPreferencesPlaybackPreferencesStore
import app.logdate.wear.v2.playback.noteId
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Resolves the moment player and its media cache, speed and engine from the real Koin graph on a
 * Wear OS emulator, saves a speed to the real SharedPreferences file, and plays a recording on the
 * watch through the real engine.
 */
@RunWith(AndroidJUnit4::class)
class MomentPlayerGraphTest {
    private val koin get() = KoinJavaComponent.getKoin()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = koin.get<PlaybackPreferencesStore>()
    private val player get() = koin.get<MomentPlayer>()

    @Before
    fun setUp() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        LogDateNotificationRegistrar(context).registerChannel(LogDateNotificationChannelKey.AUDIO_PLAYBACK)
    }

    @After
    fun tearDown() {
        runOnMainSync { player.stop() }
        preferences.setSpeed(PlaybackSpeed.NORMAL)
        preferences.setSkipSilence(false)
        context.stopService(Intent(context, AudioPlaybackService::class.java))
    }

    @Test
    fun the_player_and_the_media_cache_come_from_the_graph() {
        assertEquals(PlaybackState.Idle, player.state.value)
        assertIs<SyncedAudioWatchMediaCache>(koin.get<WatchMediaCache>())
        assertTrue(koin.get<WatchMediaCache>().cachedFile(Uuid.random()) == null)
    }

    @Test
    fun a_chosen_speed_is_saved_in_the_playback_preferences_file() {
        runOnMainSync { player.cycleSpeed() }

        val saved = context.getSharedPreferences(SharedPreferencesPlaybackPreferencesStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        assertEquals(PlaybackSpeed.FASTER, preferences.speed.value)
        assertEquals("1.5", saved.getString("speed", null))
    }

    @Test
    fun a_recording_on_the_watch_plays_or_says_there_is_no_output() {
        val note = voiceNoteOnTheWatch()

        runOnMainSync { player.playOrPause(note) }

        waitUntil("the note plays, or the output is refused") {
            val state = player.state.value
            state is PlaybackState.Active || (state is PlaybackState.Failed && state.reason == PlaybackFailure.NO_OUTPUT)
        }
        assertEquals(note.uid, player.state.value.noteId)
    }

    private fun voiceNoteOnTheWatch(): JournalNote.Audio {
        val dataSize = SAMPLE_RATE * 2 * DURATION_SECONDS
        val file = File(context.cacheDir, "moment-player-graph.wav")
        val bytes = ByteBuffer.allocate(WAV_HEADER_BYTES + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray())
        bytes.putInt(36 + dataSize)
        bytes.put("WAVEfmt ".toByteArray())
        bytes.putInt(16)
        bytes.putShort(1)
        bytes.putShort(1)
        bytes.putInt(SAMPLE_RATE)
        bytes.putInt(SAMPLE_RATE * 2)
        bytes.putShort(2)
        bytes.putShort(16)
        bytes.put("data".toByteArray())
        bytes.putInt(dataSize)
        file.writeBytes(bytes.array())
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        return JournalNote.Audio(
            mediaRef = file.absolutePath,
            durationMs = DURATION_SECONDS * 1_000L,
            creationTimestamp = now,
            lastUpdated = now,
        )
    }

    private fun runOnMainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun waitUntil(
        description: String,
        condition: () -> Boolean,
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < TIMEOUT_MS) {
            if (condition()) return
            Thread.sleep(100)
        }
        error("Timed out waiting until $description; the player is ${player.state.value}")
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val SAMPLE_RATE = 8_000
        const val DURATION_SECONDS = 30
        const val WAV_HEADER_BYTES = 44
    }
}
