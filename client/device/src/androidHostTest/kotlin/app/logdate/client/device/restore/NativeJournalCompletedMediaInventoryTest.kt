package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeJournalCompletedMediaInventoryTest {
    @Test
    fun `completed v3 transfer retains authenticated inventory after source cleanup`() =
        fixture { context, source ->
            val files = sourceFiles(source, withInventory = true)
            val authority = complete(context, files)
            assertEquals(
                "files/user_media/photo.jpg",
                authority
                    .completedMediaInventory()
                    ?.files
                    ?.single()
                    ?.path,
            )
            check(files.getValue(NATIVE_MEDIA_INVENTORY_PATH).delete())
            assertEquals(
                "files/user_media/photo.jpg",
                NativeJournalRestoreAuthority(context)
                    .completedMediaInventory()
                    ?.files
                    ?.single()
                    ?.path,
            )
        }

    @Test
    fun `v2 remains a valid completed journal without legacy media authority`() =
        fixture { context, source ->
            val authority = complete(context, sourceFiles(source, withInventory = false))
            assertNull(authority.completedMediaInventory())
            assertFalse(authority.retainCompletedMediaInventory(File(source, "missing")))
        }

    @Test
    fun `late inventory becomes usable only after its declared bytes arrive`() =
        fixture { context, source ->
            val files = sourceFiles(source, withInventory = true)
            val inventory = files.getValue(NATIVE_MEDIA_INVENTORY_PATH)
            val authority = complete(context, files - NATIVE_MEDIA_INVENTORY_PATH)
            assertNull(authority.completedMediaInventory())
            assertTrue(authority.retainCompletedMediaInventory(inventory))
            assertEquals(
                "files/user_media/photo.jpg",
                authority
                    .completedMediaInventory()
                    ?.files
                    ?.single()
                    ?.path,
            )
        }

    @Test
    fun `completed replay recreates missing retained inventory before returning`() =
        fixture { context, source ->
            val files = sourceFiles(source, withInventory = true)
            val authority = complete(context, files)
            val retained = File(context.noBackupFilesDir, "native-journal-transfer-completed-media-inventory")
            check(retained.delete())
            assertNull(authority.completedMediaInventory())
            authority.rememberCompletedSnapshot(retainedJournalChecksum(files.getValue(NATIVE_SOURCE_RECEIPT_PATH)), files)
            assertEquals(
                "files/user_media/photo.jpg",
                authority
                    .completedMediaInventory()
                    ?.files
                    ?.single()
                    ?.path,
            )
        }

    private fun complete(
        context: Context,
        files: Map<String, File>,
    ): NativeJournalRestoreAuthority {
        val authority = NativeJournalRestoreAuthority(context)
        val receipt = retainedJournalChecksum(files.getValue(NATIVE_SOURCE_RECEIPT_PATH))
        authority.begin(resumeInterrupted = false)
        authority.prepareInstallation(
            receipt,
            context.getDatabasePath("logdate"),
            retainedJournalChecksum(files.getValue("database/logdate")),
        )
        authority.rememberCompletedSnapshot(receipt, files)
        return authority
    }

    private fun sourceFiles(
        source: File,
        withInventory: Boolean,
    ): Map<String, File> {
        val files = File(source, "files").apply { mkdirs() }
        val image =
            File(files, "user_media/photo.jpg").apply {
                parentFile!!.mkdirs()
                writeText("source original")
            }
        val inventory = File(source, NATIVE_MEDIA_INVENTORY_PATH)
        writeNativeJournalMediaInventory(inventory, files)
        val preferencesFile = File(source, "preferences").apply { writeText("sender preferences") }
        val database = File(source, "database").apply { writeText("encrypted journal") }
        val receipt = File(source, NATIVE_SOURCE_RECEIPT_PATH)
        writeNativeJournalSourceReceipt(
            receipt,
            mutablePreferencesOf(
                stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                stringPreferencesKey("backend_url") to "https://cloud.example",
            ),
            preferencesFile,
            mapOf("database/logdate" to database),
            mediaInventory = inventory.takeIf { withInventory },
        )
        assertTrue(image.isFile)
        return buildMap {
            put(NATIVE_SOURCE_RECEIPT_PATH, receipt)
            put("database/logdate", database)
            put("files/logdate.preferences_pb", preferencesFile)
            if (withInventory) put(NATIVE_MEDIA_INVENTORY_PATH, inventory)
        }
    }

    private fun fixture(block: (Context, File) -> Unit) {
        val root = Files.createTempDirectory("completed-media-inventory-").toFile().canonicalFile
        try {
            val context = mockk<Context>()
            every { context.noBackupFilesDir } returns File(root, "no-backup").apply { mkdirs() }
            every { context.filesDir } returns File(root, "files").apply { mkdirs() }
            every { context.getDatabasePath(any()) } answers { File(root, "database/${firstArg<String>()}") }
            block(context, File(root, "source").apply { mkdirs() })
        } finally {
            check(root.deleteRecursively())
        }
    }
}
