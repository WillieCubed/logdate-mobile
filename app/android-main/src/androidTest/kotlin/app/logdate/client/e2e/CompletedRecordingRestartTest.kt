package app.logdate.client.e2e

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.logdate.client.media.audio.CrashSafeRecording
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The host stops the process and launches the real Activity between seed and verify. */
@RunWith(AndroidJUnit4::class)
class CompletedRecordingRestartTest {
    @Test
    fun completedRecordingSurvivesForegroundRestart() {
        val arguments = InstrumentationRegistry.getArguments()
        val mode = arguments.getString("recordingRecoveryMode")
        assumeTrue("requires the recording recovery host sequence", mode == "seed" || mode == "verify")
        val context = ApplicationProvider.getApplicationContext<Context>()
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        require(context.packageName.endsWith(".debug"))
        val label = requireNotNull(arguments.getString("recordingRecoveryFixture"))
        require(label.matches(Regex("[a-z0-9-]{1,80}")))
        val final = File(context.filesDir, "audio_notes/restore-salvage-$label.m4a")
        val staged = CrashSafeRecording.backupExcludedFile(final)
        val expected =
            InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open(
                    "recovery/completed-recording.m4a",
                ).use { it.readBytes() }
        if (mode == "seed") {
            check(!final.exists() && !staged.exists())
            staged.writeBytes(expected)
            assertTrue(CrashSafeRecording.markCompletedForRetry(staged))
            assertFalse(final.exists())
            return
        }
        assertTrue(final.isFile, "Foreground startup must publish completed audio before opening the journal")
        assertContentEquals(expected, final.readBytes())
        assertFalse(staged.exists())
        assertFalse(File(staged.parentFile, "${staged.name}.ready").exists())
        val player = MediaPlayer()
        try {
            player.setDataSource(final.absolutePath)
            player.setVolume(0f, 0f)
            player.prepare()
            assertTrue(player.duration >= 500)
            player.start()
            val deadline = SystemClock.elapsedRealtime() + 3_000
            while (player.currentPosition == 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            assertTrue(player.currentPosition > 0, "The recovered recording must decode offline")
        } finally {
            player.release()
            check(final.delete())
        }
    }
}
