package app.logdate.tools.restoredriver

import android.media.MediaExtractor
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.regex.Pattern
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Runs outside LogDate so SIGKILL cannot terminate the test or finalize its recorder. */
@RunWith(AndroidJUnit4::class)
class NativeLiveRecordingDeathTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val app = "studio.hypertext.logdate.debug"
    private val component = "$app/app.logdate.client.MainActivity"
    private val output get() = File(checkNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")))

    @Test
    fun abruptLiveRecorderDeathRetainsAudioAndReopensThroughDraftsWithoutCapture() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        requireAttestedApks()
        shell("pm grant $app android.permission.RECORD_AUDIO")
        shell("pm grant $app android.permission.POST_NOTIFICATIONS")
        shell(
            "am start -W -n $component -a app.logdate.action.CAPTURE --es mode voice " +
                "--ez app.logdate.client.testing.onboarding.SKIP true",
        )
        check(device.wait(Until.hasObject(By.text("Finish")), 30_000)) { "Voice capture controls did not appear" }
        val pid = shell("pidof $app").trim()
        check(pid.matches(Regex("[0-9]+")))
        var rawPath = ""
        waitFor("The live encoder must write recoverable AAC frames") {
            rawPath =
                shell("run-as $app find files/audio_notes -type f")
                    .trim()
                    .lineSequence()
                    .firstOrNull { it.endsWith(".aac") }
                    .orEmpty()
            rawPath.matches(Regex("""files/audio_notes/[a-zA-Z0-9_./-]+\.aac""")) &&
                shell("run-as $app stat -c %s $rawPath").trim().toLongOrNull()?.let { it > 1_024 } == true
        }
        val draftBytes = shellBytes("run-as $app cat files/datastore/entry_drafts.preferences_pb")
        val draftId =
            checkNotNull(Regex("""["]id["]:["]([a-f0-9-]{36})["]""").find(draftBytes.decodeToString())?.groupValues?.get(1))
        capture("live-before-sigkill")
        // SIGKILL bypasses activity/service destruction and MediaRecorder.stop(). No data is reset.
        shell("run-as $app kill -9 $pid")
        waitFor("The original recording process must die") { shell("pidof $app").trim() != pid }
        val captured = shellBytes("run-as $app cat $rawPath")
        assertTrue(captured.size > 1_024)
        File(output, "interrupted.aac").writeBytes(captured)
        val expected = adtsPayload(captured)
        assertTrue(expected.isNotEmpty(), "The real microphone must have produced complete AAC frames")
        assertTrue(shellBytes("run-as $app cat files/datastore/entry_drafts.preferences_pb").decodeToString().contains(draftId))

        // Natural launcher -> Add entry -> Drafts. No recording-return extra or capture intent.
        device.pressHome()
        device.waitForIdle()
        val launchResult =
            shell("am start -W -n $component -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000")
        File(output, "launcher-return.txt").writeText(launchResult)
        assertTrue(launchResult.contains("Status: ok"), "Android must accept the normal launcher intent")
        check(device.wait(Until.hasObject(By.pkg(app)), 30_000))
        waitFor("Startup must not restart the recording service") { !serviceRunning() }
        assertNotEquals(pid, shell("pidof $app").trim())
        waitFor("Cold launch must expose Home or the restored recording") { hasCreateEntry() || hasRecoveryControls() }
        if (!hasRecoveryControls()) {
            clickCreateEntry()
            clickDescription("Return to entry")
            check(device.wait(Until.gone(By.desc("Return to entry")), 15_000))
            clickDescription("Entry options")
            waitFor("The persisted recording draft must become available after startup loading") {
                device.hasObject(By.text(Pattern.compile("Drafts \\([1-9][0-9]*\\)")))
            }
            checkNotNull(device.findObject(By.textStartsWith("Drafts ("))).click()
            check(device.wait(Until.hasObject(By.text("Drafts")), 15_000))
            capture("interrupted-draft-discoverable")
            val draft = checkNotNull(device.wait(Until.findObject(By.text(Pattern.compile("Empty draft|Record audio"))), 15_000))
            draft.click()
        }
        waitFor("Recovered take must expose playback or an honest recovery failure") {
            hasRecoveryControls()
        }
        capture("interrupted-draft-reopened")
        assertFalse(serviceRunning(), "Opening an interrupted draft must not restart capture")
        assertFalse(device.hasObject(By.text("Finish")))
        val finalPath = rawPath.replace("/staging/", "/").removeSuffix(".aac") + ".m4a"
        val final = shellBytes("run-as $app cat $finalPath")
        assertTrue(final.size > 0, "This emulator's complete ADTS frames must be salvaged")
        val recovered = File(output, "recovered.m4a").apply { writeBytes(final) }
        assertContentEquals(expected, encodedSamples(recovered), "Every complete encoded frame from the killed recording must survive")
        File(output, "live-death-receipt.json").writeText(
            JSONObject()
                .put("sourcePid", pid)
                .put("destinationPid", shell("pidof $app").trim())
                .put("draftId", draftId)
                .put("rawBytes", captured.size)
                .put("completePayloadBytes", expected.size)
                .put("recoveredBytes", final.size)
                .put("micRestarted", serviceRunning())
                .toString(2),
        )
    }

    private fun clickCreateEntry() {
        waitFor("Home must expose Add entry") { hasCreateEntry() }
        (
            device.findObject(By.desc("Add a memory")) ?: device.findObject(By.desc("Create new entry"))
                ?: device.findObject(By.text("Create Entry"))
        ).click()
    }

    private fun hasCreateEntry(): Boolean =
        device.hasObject(By.desc("Add a memory")) ||
            device.hasObject(By.desc("Create new entry")) ||
            device.hasObject(By.text("Create Entry"))

    private fun hasRecoveryControls(): Boolean = device.hasObject(By.desc("Play")) || device.hasObject(By.text("Try recovery again"))

    private fun clickDescription(description: String) {
        checkNotNull(device.wait(Until.findObject(By.desc(description)), 15_000)).click()
    }

    private fun serviceRunning(): Boolean = shell("dumpsys activity services $app").contains("AudioRecordingService")

    private fun capture(name: String) {
        check(output.isDirectory || output.mkdirs())
        assertTrue(device.takeScreenshot(File(output, "$name.png")))
    }

    private fun waitFor(
        message: String,
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!predicate()) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                capture("failure")
                device.dumpWindowHierarchy(File(output, "failure-window.xml"))
                error(message)
            }
            Thread.sleep(100)
        }
    }

    private fun shell(command: String): String = shellBytes(command).decodeToString()

    private fun shellBytes(command: String): ByteArray =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }

    private fun adtsPayload(bytes: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream()
        var offset = 0
        while (offset + 7 <= bytes.size) {
            check(bytes[offset].toInt() and 0xff == 0xff && bytes[offset + 1].toInt() and 0xf6 == 0xf0)
            val length =
                ((bytes[offset + 3].toInt() and 3) shl 11) or ((bytes[offset + 4].toInt() and 0xff) shl 3) or
                    ((bytes[offset + 5].toInt() and 0xe0) shr 5)
            val header = if (bytes[offset + 1].toInt() and 1 == 1) 7 else 9
            check(length >= header)
            if (offset + length > bytes.size) break
            payload.write(bytes, offset + header, length - header)
            offset += length
        }
        return payload.toByteArray()
    }

    private fun encodedSamples(file: File): ByteArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            extractor.selectTrack(0)
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val samples = ByteArrayOutputStream()
            while (true) {
                buffer.clear()
                val length = extractor.readSampleData(buffer, 0)
                if (length < 0) break
                val sample = ByteArray(length)
                buffer.get(sample)
                samples.write(sample)
                extractor.advance()
            }
            return samples.toByteArray()
        } finally {
            extractor.release()
        }
    }
}
