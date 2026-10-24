package app.logdate.client.device.restore

import android.content.Context
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeJournalCustodyStatusReaderTest {
    @Test
    fun `empty custody is clear and a raw database fragment needs transfer`() =
        fixture { context, _ ->
            val status = NativeJournalCustodyStatusReader(context)
            assertEquals(NativeRestoreCustodyStatus.CLEAR, status.read())
            NativeJournalReplayCustody(context).capture("database/logdate", 3, "raw".byteInputStream())
            assertEquals(NativeRestoreCustodyStatus.NEEDS_TRANSFER, status.read())
        }

    @Test
    fun `verified source is recovering while unknown or incomplete payload needs transfer`() =
        fixture { context, root ->
            val database = File(root, "sender-db").apply { writeText("sender journal") }
            val preferences = File(root, "sender-prefs").apply { writeText("sender preferences") }
            val inventory =
                File(
                    root,
                    "sender-inventory",
                ).apply { writeText(Json.encodeToString(NativeJournalMediaInventory(1, emptyList()))) }
            val senderOwner = File(root, "sender-owner").apply { writeText("sender ownership") }
            val receipt = File(root, "source-receipt")
            writeNativeJournalSourceReceipt(
                receipt,
                mutablePreferencesOf(
                    stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                    stringPreferencesKey("backend_url") to "https://cloud.example",
                ),
                preferences,
                mapOf("database/logdate" to database),
                inventory,
            )
            listOf(
                "database/logdate" to database,
                "files/logdate.preferences_pb" to preferences,
                NATIVE_SOURCE_RECEIPT_PATH to receipt,
                NATIVE_MEDIA_INVENTORY_PATH to inventory,
                "sender_owner" to senderOwner,
            ).forEach { (path, file) ->
                file.inputStream().use {
                    NativeJournalReplayCustody(context).capture(
                        NATIVE_JOURNAL_TRANSFER_PREFIX + path,
                        file.length(),
                        it,
                    )
                }
            }
            val status = NativeJournalCustodyStatusReader(context)
            assertEquals(NativeRestoreCustodyStatus.RECOVERING, status.read())
            NativeJournalReplayCustody(context).capture("files/user_media/unknown", 7, "unknown".byteInputStream())
            assertEquals(NativeRestoreCustodyStatus.NEEDS_TRANSFER, status.read())
        }

    @Test
    fun `interrupted payload stays actionable after a sealed partial revision`() =
        fixture { context, _ ->
            assertFailsWith<IOException> {
                NativeJournalReplayCustody(context).capture(
                    "files/media/objects/sha256/aa/${"a".repeat(64)}.jpg",
                    10,
                    "short".byteInputStream(),
                )
            }
            assertEquals(NativeRestoreCustodyStatus.NEEDS_TRANSFER, NativeJournalCustodyStatusReader(context).read())
        }

    @Test
    fun `raw staged key is recovering only with exact current scope proof`() =
        fixture { context, _ ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture("files/db_recovery/passphrase_backup", 3, "key".byteInputStream())
            val physical =
                custody
                    .sealedPayloads()
                    .single()
                    .file.canonicalPath

            assertEquals(NativeRestoreCustodyStatus.NEEDS_TRANSFER, NativeJournalCustodyStatusReader(context).read())
            assertEquals(
                NativeRestoreCustodyStatus.RECOVERING,
                NativeJournalCustodyStatusReader(context, isRecoverableStagedAuxiliary = { it.file.canonicalPath == physical }).read(),
            )
            assertEquals(
                NativeRestoreCustodyStatus.NEEDS_TRANSFER,
                NativeJournalCustodyStatusReader(context, isRecoverableStagedAuxiliary = { false }).read(),
            )
        }

    @Test
    fun `legacy callback is recovering only when an admitted media inventory matches its bytes`() =
        fixture { context, root ->
            val path = "files/user_media/older-original.jpg"
            val original = File(root, "older-original").apply { writeText("original") }
            original.inputStream().use { NativeJournalReplayCustody(context).capture(path, original.length(), it) }
            val entry = NativeJournalMediaInventory.Entry(path, original.length(), retainedJournalChecksum(original))
            val inventory = NativeJournalMediaInventory(1, listOf(entry))

            assertEquals(NativeRestoreCustodyStatus.NEEDS_TRANSFER, NativeJournalCustodyStatusReader(context).read())
            assertEquals(
                NativeRestoreCustodyStatus.RECOVERING,
                NativeJournalCustodyStatusReader(context, stagedMediaInventories = { listOf(inventory) }).read(),
            )
            assertEquals(
                NativeRestoreCustodyStatus.NEEDS_TRANSFER,
                NativeJournalCustodyStatusReader(
                    context,
                    stagedMediaInventories = {
                        listOf(NativeJournalMediaInventory(1, listOf(entry.copy(sha256 = "0".repeat(64)))))
                    },
                ).read(),
            )
        }

    private fun fixture(block: (Context, File) -> Unit) {
        val root = Files.createTempDirectory("native-custody-status-").toFile()
        try {
            val context = mockk<Context>()
            every { context.noBackupFilesDir } returns File(root, "no-backup").apply { mkdirs() }
            every { context.filesDir } returns File(root, "files").apply { mkdirs() }
            block(context, root)
        } finally {
            check(root.deleteRecursively())
        }
    }
}
