package app.logdate.client.device.restore

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeCompletedRestoreReplayTest {
    @Test
    fun `completed raw delivery routes later bytes to custody without new generation or account work`() =
        fixture { context ->
            val detector = PostRestoreDetector(context)
            detector.beginNativeRestoreSession()
            context.getDatabasePath("logdate").apply {
                parentFile!!.mkdirs()
                writeText("accepted journal")
            }
            detector.markNativeRestorePayloadReceived(databaseDelivered = true)
            detector.markNativePrivacyPayloadReceived()
            detector.reserveAccountRecoveryForCompletion()
            detector.recordRestoreCompletedUnderDeliveryLock()
            val before = assertNotNull(detector.capturePrivacyRepairIdentity())
            val custody = NativeJournalReplayCustody(context)
            val replay = NativeCompletedRestoreReplay(context, custody)
            assertTrue(replay.shouldCapture(), "Raw completion has no immutable snapshot receipt")
            custody.capture("database/logdate", 8, "incoming".byteInputStream())
            var scheduled = 0
            assertTrue(
                replay.finishIfRequired {
                    scheduled++
                    true
                },
            )
            assertEquals(1, scheduled)
            assertEquals(before, detector.capturePrivacyRepairIdentity())
            assertEquals("accepted journal", context.getDatabasePath("logdate").readText())
            assertEquals(
                "incoming",
                custody
                    .sealedPayloads()
                    .single()
                    .file
                    .readText(),
            )
            assertTrue(NativeCompletedRestoreReplay(context, custody).shouldCapture(), "Agent recreation retains completed authority")
        }

    @Test
    fun `unfinished fresh delivery cannot be finalized as completed raw replay`() =
        fixture { context ->
            val detector = PostRestoreDetector(context)
            val custody = NativeJournalReplayCustody(context)
            val replay = NativeCompletedRestoreReplay(context, custody)
            assertFalse(replay.shouldCapture())
            detector.beginNativeRestoreSession()
            custody.capture("database/logdate", 8, "incoming".byteInputStream())
            assertFalse(replay.finishIfRequired { error("Fresh delivery must finish its own native session") })
            assertFalse(replay.shouldCapture())
        }

    @Test
    fun `selected media alone keeps the established media completion path`() =
        fixture { context ->
            val detector = PostRestoreDetector(context)
            detector.beginNativeRestoreSession()
            detector.markNativePrivacyPayloadReceived()
            detector.reserveAccountRecoveryForCompletion()
            detector.recordRestoreCompletedUnderDeliveryLock()
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/audio_notes/take.m4a", 5, "audio".byteInputStream())
            val replay = NativeCompletedRestoreReplay(context, custody)
            assertTrue(replay.shouldCapture())
            assertFalse(replay.finishIfRequired { error("Media completion owns its own wake-up") })
            assertTrue(custody.hasInputs())
        }

    private fun fixture(block: (Context) -> Unit) {
        val root = Files.createTempDirectory("completed-raw-replay").toFile()
        val files = File(root, "files").apply { mkdirs() }
        val privateRoot = File(root, "private").apply { mkdirs() }
        val info = ApplicationInfo().apply { dataDir = root.path }
        val context =
            mockk<Context> {
                every { filesDir } returns files
                every { noBackupFilesDir } returns privateRoot
                every { applicationInfo } returns info
                every { getDatabasePath(any()) } answers { File(root, "database/" + firstArg<String>()) }
            }
        try {
            block(context)
        } finally {
            root.deleteRecursively()
        }
    }
}
