package app.logdate.client.e2e

import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.uiautomator.UiDevice
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** One owned, normal-speed, silent recording; never stops another recorder or touches app data. */
internal class PlacesSheetRecording(
    private val instrumentation: Instrumentation,
    private val device: UiDevice,
    private val output: File,
) {
    fun record(interaction: () -> Unit) {
        val token = Uuid.random().toString()
        val remoteVideo = "/sdcard/logdate-places-sheet-$token.mp4"
        val remotePid = "/sdcard/logdate-places-sheet-$token.pid"
        val script = File(output, "record-$token.sh")
        script.writeText("#!/system/bin/sh\necho \$\$ > $remotePid\nexec screenrecord --bit-rate 3000000 --time-limit 30 $remoteVideo\n")
        val stream =
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("sh ${script.absolutePath}"),
            )
        val executor = Executors.newSingleThreadExecutor()
        val reader =
            executor.submit {
                stream.use { input -> File(output, "screenrecord.log").outputStream().use { input.copyTo(it) } }
            }
        var pid: Int? = null
        try {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (pid == null && SystemClock.elapsedRealtime() < deadline) {
                pid = device.executeShellCommand("cat $remotePid").trim().toIntOrNull()
                SystemClock.sleep(50)
            }
            checkNotNull(pid) { "Owned recorder did not start" }
            SystemClock.sleep(800)
            interaction()
            SystemClock.sleep(800)
        } finally {
            pid?.let {
                val command = device.executeShellCommand("cat /proc/$it/cmdline")
                if (command.contains(remoteVideo)) device.executeShellCommand("kill -2 $it")
            }
            try {
                reader.get(35, TimeUnit.SECONDS)
            } finally {
                executor.shutdown()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
            val video = File(output, "places-sheet-interaction.mp4")
            device.executeShellCommand("cp $remoteVideo ${video.absolutePath}")
            device.executeShellCommand("rm -f $remoteVideo $remotePid")
            assertTrue(video.isFile && video.length() > 10_000, "Original interaction video must be retained")
        }
    }
}
