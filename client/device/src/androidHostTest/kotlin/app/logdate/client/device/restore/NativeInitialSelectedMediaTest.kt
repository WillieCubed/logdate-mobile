package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeInitialSelectedMediaTest {
    @Test
    fun `completed snapshot without authenticated media inventory retains initial bytes`() =
        fixture {
            capture()
            complete(withInventory = false)
            assertFalse(initial.promoteCompleted())
            assertTrue(initial.hasInputs())
            assertFalse(NativeJournalReplayCustody(context).hasInputs())
        }

    @Test
    fun `first selected media cannot divert subsequent snapshot files into replay`() =
        fixture {
            capture()
            assertTrue(initial.hasInputs())
            assertFalse(NativeJournalReplayCustody(context).hasInputs())
            assertFalse(initial.promoteCompleted())
            assertFalse(File(context.filesDir, "media/original.jpg").exists())
        }

    @Test
    fun `installed exact inventory promotes immutable bytes and recreated retry is idempotent`() =
        fixture {
            capture()
            complete()
            assertTrue(initial.promoteCompleted())
            assertFalse(initial.hasInputs())
            val replay = NativeJournalReplayCustody(context)
            val payload = replay.sealedPayloads().single()
            assertEquals(PATH, payload.path)
            assertEquals("selected original", payload.file.readText())
            assertTrue(payload.complete)
            assertFalse(NativeInitialSelectedMedia(context).promoteCompleted())
            assertEquals(1, replay.sealedPayloads().size)
            assertEquals("occupied local journal", database.readText())
            assertFalse(File(context.filesDir, "media/original.jpg").exists())
        }

    @Test
    fun `unlisted and changed revisions remain private and block another backup`() =
        fixture {
            capture("wrong bytes")
            initial.capture("files/media/unlisted.jpg", 4, "more".byteInputStream())
            complete()
            assertFalse(initial.promoteCompleted())
            assertTrue(initial.hasInputs())
            assertFalse(NativeJournalReplayCustody(context).hasInputs())
            for (transfer in listOf(false, true)) {
                assertFailsWith<IllegalStateException> { requireRetainedJournalBackupReady(context, transfer) }
            }
        }

    @Test
    fun `truncated declared payload is retained but never promoted`() =
        fixture {
            assertFailsWith<IOException> { initial.capture(PATH, 100, "short".byteInputStream()) }
            complete()
            assertFalse(initial.promoteCompleted())
            assertTrue(initial.hasInputs())
            assertFalse(NativeJournalReplayCustody(context).hasInputs())
        }

    @Test
    fun `tampering with sealed source is rejected without touching installed data`() =
        fixture {
            capture()
            complete()
            val root = File(context.noBackupFilesDir, NativeInitialSelectedMedia.DIRECTORY)
            root.walkTopDown().single { it.name == "payload" }.appendText("tampered")
            assertFailsWith<IllegalStateException> { initial.promoteCompleted() }
            assertTrue(initial.hasInputs())
            assertFalse(NativeJournalReplayCustody(context).hasInputs())
            assertEquals("occupied local journal", database.readText())
        }

    @Test
    fun `traversal unknown roots and symlink destinations are rejected`() =
        fixture {
            for (path in listOf("files/media/../secret", "files/media/./x", "database/logdate", "files/media//x")) {
                assertFailsWith<IllegalArgumentException> { initial.capture(path, 1, "x".byteInputStream()) }
            }
            val media = File(context.filesDir, "media").apply { mkdirs() }
            val outside = File(root, "outside").apply { mkdirs() }
            Files.createSymbolicLink(File(media, "link").toPath(), outside.toPath())
            assertFailsWith<IllegalStateException> { NativeInitialSelectedMedia.destinationPath(context, File(media, "link/original")) }
            assertEquals(null, NativeInitialSelectedMedia.destinationPath(context, database))
        }

    private fun fixture(block: Fixture.() -> Unit) = Fixture().use { it.block() }

    private class Fixture : AutoCloseable {
        val root = Files.createTempDirectory("initial-selected-media-").toFile()
        val context = mockk<Context>()
        val database = File(root, "database/logdate").apply { parentFile!!.mkdirs() }
        val initial: NativeInitialSelectedMedia

        init {
            every { context.noBackupFilesDir } returns File(root, "private").apply { mkdirs() }
            every { context.filesDir } returns File(root, "files").apply { mkdirs() }
            every { context.getDatabasePath(any()) } answers { File(root, "database/${firstArg<String>()}") }
            initial = NativeInitialSelectedMedia(context)
        }

        fun capture(bytes: String = "selected original") = initial.capture(PATH, bytes.length.toLong(), bytes.byteInputStream())

        fun complete(withInventory: Boolean = true) {
            val source = File(root, "source").apply { mkdirs() }
            val media =
                File(source, "media/original.jpg").apply {
                    parentFile!!.mkdirs()
                    writeText("selected original")
                }
            val inventory = File(source, "inventory.json")
            writeNativeJournalMediaInventory(inventory, source, setOf(PATH))
            val db = File(source, "database").apply { writeText("authenticated snapshot") }
            val prefs = File(source, "preferences").apply { writeText("source preferences") }
            val receipt = File(source, "receipt")
            writeNativeJournalSourceReceipt(
                receipt,
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                ),
                prefs,
                mapOf("database/logdate" to db),
                inventory.takeIf { withInventory },
            )
            val authority = NativeJournalRestoreAuthority(context)
            authority.begin(resumeInterrupted = false)
            val hash = retainedJournalChecksum(receipt)
            authority.prepareInstallation(hash, database, retainedJournalChecksum(db))
            database.writeText("occupied local journal")
            authority.rememberCompletedSnapshot(
                hash,
                mapOf(
                    "database/logdate" to db,
                    "files/logdate.preferences_pb" to prefs,
                    NATIVE_SOURCE_RECEIPT_PATH to receipt,
                ) + if (withInventory) mapOf(NATIVE_MEDIA_INVENTORY_PATH to inventory) else emptyMap(),
            )
            assertTrue(media.exists())
        }

        override fun close() {
            check(root.deleteRecursively())
        }
    }

    companion object {
        private const val PATH = "files/media/original.jpg"
    }
}
