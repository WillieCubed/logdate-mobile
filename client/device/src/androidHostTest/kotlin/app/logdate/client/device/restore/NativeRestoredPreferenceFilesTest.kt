package app.logdate.client.device.restore

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.AtomicFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeRestoredPreferenceFilesTest {
    private val directory = createTempDirectory("native-preference-finalization").toFile()
    private val noBackup = File(directory, "private").apply { mkdirs() }
    private val files = File(directory, "files").apply { mkdirs() }
    private val context =
        mockk<Context>().also {
            every { it.noBackupFilesDir } returns noBackup
            every { it.filesDir } returns files
            every { it.applicationInfo } returns ApplicationInfo().apply { dataDir = File(directory, "data").path }
            every { it.getDatabasePath(any()) } answers { File(directory, firstArg<String>()) }
        }
    private val detector = PostRestoreDetector(context)
    private val retained = NativeRestoredPreferenceFiles(context)
    private val names = setOf("logdate_widget_instances")

    @AfterTest
    fun cleanup() {
        unmockkConstructor(AtomicFile::class)
        directory.deleteRecursively()
    }

    @Test
    fun `unrelated restored files do not require optional preference storage`() {
        File(noBackup, "native-restored-preferences").writeText("Obstructed optional preference directory")

        assertFalse(
            retained.restore(
                context.getDatabasePath("logdate"),
                names,
                100,
                { error("Unrelated input belongs to the caller") },
                { error("The caller owns unrelated file delivery") },
                { error("Not a preference duplicate") },
            ),
        )
    }

    @Test
    fun `truncated input stays unconfirmed and a complete retry repairs it`() {
        snapshot("pending")
        val destination = File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml")
        destination.parentFile?.mkdirs()
        val short = "<map/>".encodeToByteArray()

        assertFailsWith<IOException> {
            retained.restore(destination, names, 100, {
                short
            }, { error("Incomplete input must not reach the platform copier") }, { error("Not accepted") })
        }
        assertFailsWith<IOException> { retained.recordFinalDelivery(names) }
        assertFalse(detector.hasCompletedNativeRestoreDelivery())

        val complete = "<map><string name=\"7.chosenNoteId\">complete-entry</string></map>".encodeToByteArray()
        assertTrue(
            retained.restore(
                destination,
                names,
                complete.size.toLong(),
                { complete },
                { destination.writeBytes(it) },
                { error("Retry") },
            ),
        )
        assertEquals(names, retained.recordFinalDelivery(names))
        assertEquals(complete.size.toLong(), destination.length())
    }

    @Test
    fun `completion without a final manifest resumes the active generation`() {
        detector.beginNativeRestoreSession(retainPreferences = true)
        val generation = assertNotNull(detector.retainedPreferenceSessionId())

        assertTrue(retained.canResumeFinalization(names))
        assertEquals(generation, detector.retainedPreferenceSessionId())
    }

    @Test
    fun `completion recreates delivered decision from a captured snapshot without callback memory`() {
        val generation = snapshot("captured", "AA==")

        assertEquals(names, NativeRestoredPreferenceFiles(context).recordFinalDelivery(names))
        assertEquals(generation, detector.retainedPreferenceSessionId())
    }

    @Test
    fun `completion retains a successful copy whose byte snapshot could not persist`() {
        snapshot("delivered")

        assertEquals(names, NativeRestoredPreferenceFiles(context).recordFinalDelivery(names))
    }

    @Test
    fun `completion cannot publish omission for a copy with unknown outcome`() {
        snapshot("observed")

        assertFailsWith<IOException> { retained.recordFinalDelivery(names) }
        assertFalse(detector.hasCompletedNativeRestoreDelivery())
    }

    @Test
    fun `completion replays prepared bytes without another file callback`() {
        val bytes = "<map><string name=\"7.chosenNoteId\">retained-entry</string></map>".encodeToByteArray()
        snapshot(
            "prepared",
            java.util.Base64
                .getEncoder()
                .encodeToString(bytes),
        )
        val replaying =
            NativeRestoredPreferenceFiles(context) { destination, incoming ->
                destination.parentFile?.mkdirs()
                destination.writeBytes(incoming)
            }

        assertEquals(names, replaying.recordFinalDelivery(names))
        assertEquals(bytes.decodeToString(), File(context.applicationInfo.dataDir, "shared_prefs/logdate_widget_instances.xml").readText())
    }

    @Test
    fun `framework rejection retains prepared bytes and cannot complete delivery`() {
        snapshot("prepared", "AA==")
        val replaying = NativeRestoredPreferenceFiles(context) { _, _ -> }

        assertFailsWith<IOException> { replaying.recordFinalDelivery(names) }
        assertFalse(detector.hasCompletedNativeRestoreDelivery())
    }

    @Test
    fun `completed delivery cannot resume an abandoned preference generation`() {
        detector.beginNativeRestoreSession(retainPreferences = true)
        detector.recordRestoreCompleted()
        detector.markDeviceInitialized()

        assertFalse(retained.canResumeFinalization(names))
    }

    private fun snapshot(
        state: String,
        source: String? = null,
    ): String {
        detector.beginNativeRestoreSession(retainPreferences = true)
        val generation = assertNotNull(detector.retainedPreferenceSessionId())
        val snapshot = File(noBackup, "native-restored-preferences/$generation/logdate_widget_instances.json")
        snapshot.parentFile?.mkdirs()
        snapshot.writeText(
            """{"generation":"$generation","state":"$state","source":${source?.let { "\"$it\"" } ?: "null"}}""",
        )
        // Host Android stubs have no AtomicFile implementation. Keep filesystem bytes and
        // production generation/decision logic real; native acceptance covers AtomicFile itself.
        mockkConstructor(AtomicFile::class)
        every { anyConstructed<AtomicFile>().baseFile } returns snapshot
        every { anyConstructed<AtomicFile>().openRead() } answers { snapshot.inputStream() }
        every { anyConstructed<AtomicFile>().startWrite() } answers { snapshot.outputStream() }
        every { anyConstructed<AtomicFile>().finishWrite(any()) } answers { firstArg<FileOutputStream>().close() }
        every { anyConstructed<AtomicFile>().failWrite(any()) } answers { firstArg<FileOutputStream>().close() }
        return generation
    }
}
