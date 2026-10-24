package app.logdate.client.data.restore

import android.app.Application
import android.content.ContextWrapper
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import app.logdate.client.device.restore.NATIVE_MEDIA_INVENTORY_PATH
import app.logdate.client.device.restore.NATIVE_SOURCE_RECEIPT_PATH
import app.logdate.client.device.restore.NativeJournalMediaInventory
import app.logdate.client.device.restore.NativeJournalReplayCustody
import app.logdate.client.device.restore.NativeJournalRestoreAuthority
import app.logdate.client.device.restore.writeNativeJournalSourceReceipt
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = Application::class)
class NativeJournalStagedCompletionTest {
    @Test
    fun `late completed inventory is durably retained before its only callback is acknowledged`() =
        fixture { f ->
            f.capture(f.inventoryFile)
            refreshStagedNativeMediaInventories(f.context)
            assertEquals(f.inventory, NativeJournalRestoreAuthority(f.context).completedMediaInventory())
            assertFalse(NativeJournalReplayCustody(f.context).hasInputs())
            refreshStagedNativeMediaInventories(f.context)
            assertEquals(f.inventory, NativeJournalRestoreAuthority(f.context).completedMediaInventory())
        }

    @Test
    fun `wrong late manifest remains in custody and cannot authorize source media`() =
        fixture { f ->
            val wrong =
                File(
                    f.root,
                    "wrong-inventory",
                ).apply { writeText(Json.encodeToString(NativeJournalMediaInventory(1, emptyList()))) }
            f.capture(wrong)
            refreshStagedNativeMediaInventories(f.context)
            assertTrue(NativeJournalReplayCustody(f.context).hasInputs())
            assertEquals(null, NativeJournalRestoreAuthority(f.context).completedMediaInventory())
        }

    private fun fixture(block: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("native-staged-completion-").toFile()
        try {
            block(Fixture(root))
        } finally {
            check(root.deleteRecursively())
        }
    }

    private class Fixture(
        val root: File,
    ) {
        private val files = File(root, "files").apply { mkdirs() }
        private val privateFiles = File(root, "no-backup").apply { mkdirs() }
        private val database = File(root, "database/logdate").apply { parentFile!!.mkdirs() }
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
                override fun getFilesDir(): File = files

                override fun getNoBackupFilesDir(): File = privateFiles

                override fun getDatabasePath(name: String): File = File(database.parentFile, name)
            }
        val inventory =
            NativeJournalMediaInventory(1, listOf(NativeJournalMediaInventory.Entry("files/audio_notes/original", 12, "a".repeat(64))))
        val inventoryFile = File(root, "inventory").apply { writeText(Json.encodeToString(inventory)) }

        init {
            val sourceDb = File(root, "source-db").apply { writeText("verified source database boundary") }
            val sourcePrefs = File(root, "source-prefs").apply { writeText("source preferences boundary") }
            val receipt = File(root, "receipt")
            writeNativeJournalSourceReceipt(
                receipt,
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                ),
                sourcePrefs,
                mapOf("database/logdate" to sourceDb),
                inventoryFile,
            )
            val authority = NativeJournalRestoreAuthority(context)
            authority.begin(false)
            val hash = nativeSnapshotChecksum(receipt)
            authority.prepareInstallation(hash, database, nativeSnapshotChecksum(sourceDb))
            authority.rememberCompletedSnapshot(
                hash,
                mapOf(
                    "database/logdate" to sourceDb,
                    "files/logdate.preferences_pb" to sourcePrefs,
                    NATIVE_SOURCE_RECEIPT_PATH to receipt,
                ),
            )
        }

        fun capture(file: File) {
            file.inputStream().use { NativeJournalReplayCustody(context).capture(NATIVE_MEDIA_INVENTORY_PATH, file.length(), it) }
        }
    }
}
