package app.logdate.client.device.restore

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeSelectedOriginalCustodyTest {
    @Test
    fun `selected originals survive reconstruction without routing fresh journal callbacks as replays`() =
        fixture { context ->
            for (path in listOf("files/audio_notes/take.m4a", "files/user_media/photo.png", "files/media/objects/sha256/aa/asset")) {
                val bytes = "selected original".encodeToByteArray()
                NativeJournalReplayCustody(context).capture(path, bytes.size.toLong(), bytes.inputStream())
                val restored = NativeJournalReplayCustody(context)
                assertTrue(restored.hasInputs(), "Originals must still block backup until proven imported")
                assertFalse(restored.hasJournalInputs(), "Media alone must not divert the fresh snapshot into replay custody")
                val payload = restored.sealedPayloads().single { it.path == path }
                assertContentEquals(bytes, payload.file.readBytes())
                assertFalse(File(context.filesDir, path.removePrefix("files/")).exists(), "Capture must never write a live media path")
            }
        }

    @Test
    fun `partial original stays preserved without becoming a database replay`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            assertFailsWith<IOException> { custody.capture("files/audio_notes/take.m4a", 20, "partial".byteInputStream()) }
            assertTrue(custody.hasInputs())
            assertFalse(custody.hasJournalInputs())
            val payload = custody.sealedPayloads().single()
            assertFalse(payload.complete)
            assertContentEquals("partial".encodeToByteArray(), payload.file.readBytes())
        }

    @Test
    fun `ordinary source callbacks and explicit source cohorts remain journal inputs`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/logdate.preferences_pb", 5, "prefs".byteInputStream())
            assertTrue(custody.hasJournalInputs())
            custody.capture("files/audio_notes/take.m4a", 4, "take".byteInputStream())
            assertTrue(custody.hasJournalInputs())
        }

    @Test
    fun `explicit media source cohort never bypasses source routing`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.captureInCohort(
                kotlin.uuid.Uuid
                    .random()
                    .toString(),
                "files/audio_notes/take.m4a",
                4,
                "take".byteInputStream(),
            )
            assertTrue(custody.hasJournalInputs())
        }

    @Test
    fun `a missing or changed media marker cannot relax replay routing`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/audio_notes/take.m4a", 4, "take".byteInputStream())
            val marker = File(context.noBackupFilesDir, "native-journal-replay-custody").listFiles()!!.single().resolve("media-only")
            marker.writeText("unknown")
            assertTrue(custody.hasJournalInputs())
            assertTrue(marker.delete())
            assertTrue(custody.hasJournalInputs())
            assertContentEquals(
                "take".encodeToByteArray(),
                custody
                    .sealedPayloads()
                    .single()
                    .file
                    .readBytes(),
            )
        }

    @Test
    fun `a media marker cannot hide a mixed journal cohort`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/audio_notes/take.m4a", 4, "take".byteInputStream())
            val root = File(context.noBackupFilesDir, "native-journal-replay-custody")
            NativeIncomingJournal(root).capture(root.listFiles()!!.single().name, "database/logdate", 7, "journal".byteInputStream())
            assertTrue(custody.hasJournalInputs())
            assertTrue(custody.hasInputs())
        }

    @Test
    fun `callback routing reads bounded metadata while import still authenticates payload bytes`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/audio_notes/take.m4a", 4, "take".byteInputStream())
            val root = File(context.noBackupFilesDir, "native-journal-replay-custody")
            root.walkTopDown().single { it.name == "payload" }.writeText("edit")
            assertFalse(custody.hasJournalInputs(), "Routing must not rehash all prior originals on each callback")
            assertFailsWith<IllegalStateException> { custody.sealedPayloads() }
            assertTrue(custody.hasInputs(), "Unauthenticated bytes must remain in custody")
        }

    private fun fixture(test: (Context) -> Unit) {
        val root = Files.createTempDirectory("selected-originals").toFile()
        val context = mockk<Context>()
        every { context.noBackupFilesDir } returns File(root, "no-backup").apply { mkdirs() }
        every { context.filesDir } returns File(root, "files").apply { mkdirs() }
        try {
            test(context)
        } finally {
            check(root.deleteRecursively())
        }
    }
}
