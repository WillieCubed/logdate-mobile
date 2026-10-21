package app.logdate.client.e2e

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.Closeable
import java.io.File
import kotlin.concurrent.thread

/** Delays the real software encoder across the original quiet deadline on a Managed Device. */
internal class QuietSaveEncoderBackpressure(
    confirmedAt: Long,
) : Closeable {
    private val outputDirectory =
        File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")), "encoder-backpressure").apply {
            mkdirs()
        }

    @Volatile private var failure: Throwable? = null

    @Volatile private var applied = false
    private val worker =
        thread(name = "quiet-save-encoder-backpressure", isDaemon = true) {
            var suspendedPid: String? = null
            try {
                Thread.sleep((confirmedAt + 116_000 - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                check(shell("su 0 id -u") == "0") { "Encoder regression requires a rooted Gradle Managed Device" }
                val pid = shell("pidof media.swcodec")
                check(pid.isNotEmpty() && pid.all(Char::isDigit)) { "Expected one software encoder process" }
                suspendedPid = pid
                setSuspended(pid, suspended = true)
                File(outputDirectory, "quiet-save-encoder-backpressure.txt").writeText("suspendedAtMs=${SystemClock.elapsedRealtime()}\n")
                // Covers startup scheduling variation as well as the two-minute quiet deadline.
                Thread.sleep(12_000)
                applied = true
            } catch (error: Throwable) {
                failure = error
            } finally {
                suspendedPid?.let { pid ->
                    try {
                        setSuspended(pid, suspended = false)
                        File(
                            outputDirectory,
                            "quiet-save-encoder-backpressure.txt",
                        ).appendText("resumedAtMs=${SystemClock.elapsedRealtime()}\n")
                    } catch (error: Throwable) {
                        failure = error
                    }
                }
            }
        }

    fun assertApplied() {
        worker.join()
        assertNull("Encoder backpressure fixture failed: $failure", failure)
        assertTrue("Encoder backpressure must run for its full duration", applied)
    }

    override fun close() {
        worker.interrupt()
        worker.join()
    }

    private fun setSuspended(
        pid: String,
        suspended: Boolean,
    ) {
        shell("su 0 kill -${if (suspended) "STOP" else "CONT"} $pid")
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var state: String?
        do {
            state = shell("su 0 cat /proc/$pid/status").lineSequence().firstOrNull { it.startsWith("State:") }
            if (state != null && state.substringAfter(':').trim().startsWith("T") == suspended) return
            Thread.sleep(10)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("Encoder suspension=$suspended was not observed: $state")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor
            .AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
            ).bufferedReader()
            .use { it.readText().trim() }
}
