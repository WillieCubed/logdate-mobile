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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeLateMediaAcknowledgementTest {
    @Test
    fun `fully recovered canonical media cohort is durably consumed after restart`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture(MEDIA, 8, "original".byteInputStream())
            val payload = custody.sealedPayloads().single()
            NativeJournalReplayCustody(context).acknowledgeRecoveredMedia(setOf(payload.file.canonicalPath))
            assertFalse(NativeJournalReplayCustody(context).hasInputs())
        }

    @Test
    fun `recovered media retires while unrelated key callback remains in custody`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture(MEDIA, 8, "original".byteInputStream())
            custody.capture("files/db_recovery/passphrase_backup", 3, "key".byteInputStream())
            val payloads = custody.sealedPayloads()
            custody.acknowledgeRecoveredMedia(payloads.mapTo(mutableSetOf()) { it.file.canonicalPath })
            assertTrue(custody.hasInputs())
            assertTrue(payloads.single { it.path == "files/db_recovery/passphrase_backup" }.file.exists())
            assertFalse(payloads.single { it.path == MEDIA }.file.exists())
        }

    @Test
    fun `missing physical-file proof and truncated media can never be acknowledged`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            assertFailsWith<IOException> { custody.capture(MEDIA, 100, "partial".byteInputStream()) }
            val payload = custody.sealedPayloads().single()
            custody.acknowledgeRecoveredMedia(setOf(payload.file.canonicalPath))
            assertTrue(custody.hasInputs())
            assertTrue(payload.file.exists())
        }

    @Test
    fun `unknown unreferenced canonical bytes without recovery proof remain preserved`() =
        fixture { context ->
            val custody = NativeJournalReplayCustody(context)
            custody.capture(MEDIA, 8, "original".byteInputStream())
            custody.acknowledgeRecoveredMedia(emptySet())
            assertTrue(custody.hasInputs())
        }

    @Test
    fun `known prefixed snapshot replay mixed with repaired media is consumed together`() =
        fixture { context ->
            val source = rememberCompleted(context)
            val custody = NativeJournalReplayCustody(context)
            source.forEach { (path, file) ->
                file.inputStream().use { custody.capture(NATIVE_JOURNAL_TRANSFER_PREFIX + path, file.length(), it) }
            }
            custody.capture(MEDIA, 8, "original".byteInputStream())
            val media = custody.sealedPayloads().single { it.path == MEDIA }
            custody.acknowledgeRecoveredMedia(setOf(media.file.canonicalPath))
            assertFalse(custody.hasInputs())
        }

    @Test
    fun `unknown raw DB cannot borrow completed prefixed source authority when mixed with repaired media`() =
        fixture { context ->
            val source = rememberCompleted(context)
            val custody = NativeJournalReplayCustody(context)
            source.forEach { (path, file) ->
                file.inputStream().use { custody.capture(NATIVE_JOURNAL_TRANSFER_PREFIX + path, file.length(), it) }
            }
            custody.capture("database/logdate", 12, "different DB".byteInputStream())
            custody.capture(MEDIA, 8, "original".byteInputStream())
            val payloads = custody.sealedPayloads()
            custody.acknowledgeRecoveredMedia(setOf(payloads.single { it.path == MEDIA }.file.canonicalPath))
            assertTrue(custody.hasInputs())
            assertTrue(payloads.single { it.path == "database/logdate" }.file.exists())
            assertFalse(payloads.single { it.path == MEDIA }.file.exists())
        }

    private fun rememberCompleted(context: Context): Map<String, File> {
        val source = File(context.noBackupFilesDir, "completed-source").apply { mkdirs() }
        val files =
            buildMap {
                val database = File(source, "database").apply { writeText("source database") }
                val preferences = File(source, "preferences").apply { writeText("source preferences") }
                val receiptFile = File(source, "receipt")
                writeNativeJournalSourceReceipt(
                    receiptFile,
                    mutablePreferencesOf(
                        stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                        stringPreferencesKey("backend_url") to "https://cloud.example",
                    ),
                    preferences,
                    mapOf("database/logdate" to database),
                )
                put(NATIVE_SOURCE_RECEIPT_PATH, receiptFile)
                put("files/logdate.preferences_pb", preferences)
                put("database/logdate", database)
            }
        val receipt = retainedJournalChecksum(files.getValue(NATIVE_SOURCE_RECEIPT_PATH))
        val authority = NativeJournalRestoreAuthority(context)
        authority.begin(resumeInterrupted = false)
        authority.prepareInstallation(
            receipt,
            context.getDatabasePath("logdate"),
            retainedJournalChecksum(files.getValue("database/logdate")),
        )
        authority.rememberCompletedSnapshot(receipt, files)
        return files
    }

    private fun fixture(block: (Context) -> Unit) {
        val root = Files.createTempDirectory("late-media-ack-").toFile().canonicalFile
        try {
            val context = mockk<Context>()
            every { context.noBackupFilesDir } returns File(root, "no-backup").apply { mkdirs() }
            every { context.filesDir } returns File(root, "files").apply { mkdirs() }
            every { context.getDatabasePath(any()) } answers { File(root, "database/${firstArg<String>()}") }
            block(context)
        } finally {
            check(root.deleteRecursively())
        }
    }

    private companion object {
        const val MEDIA = "files/media/objects/sha256/06/0682c5f2076f099c34cfdd15a9e063849ed437a49677e6fcc5b4198c76575be5.png"
    }
}
