package app.logdate.client.e2e

import android.media.MediaPlayer
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun playRecoveryOfflineMedia(
    bytes: ByteArray,
    extension: String,
) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val source = File.createTempFile("recovery-playback-", extension, context.cacheDir)
    try {
        source.outputStream().use { it.write(bytes) }
        val player = MediaPlayer()
        try {
            val completed = CountDownLatch(1)
            val failed = AtomicBoolean(false)
            player.setOnCompletionListener { completed.countDown() }
            player.setOnErrorListener { _, _, _ ->
                failed.set(true)
                completed.countDown()
                true
            }
            player.setDataSource(source.absolutePath)
            player.prepare()
            assertTrue(player.duration > 0, "offline media has no playable duration")
            player.start()
            assertTrue(completed.await(20, TimeUnit.SECONDS), "offline media playback did not complete")
            assertFalse(failed.get(), "offline media decoder reported an error")
        } finally {
            player.release()
        }
    } finally {
        source.delete()
    }
}
