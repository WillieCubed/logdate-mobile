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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeJournalReplayCustodyTest {
    @Test
    fun `unresolved noBackup custody blocks cloud and device transfer before export`() =
        fixture { f ->
            val replay = NativeJournalReplayCustody(f.context)
            replay.capture("database/logdate", 9, "new bytes".byteInputStream())
            for (deviceTransfer in listOf(false, true)) {
                assertFailsWith<IllegalStateException> { requireRetainedJournalBackupReady(f.context, deviceTransfer) }
            }
            assertEquals(
                "new bytes",
                replay
                    .sealedPayloads()
                    .single()
                    .file
                    .readText(),
            )
        }

    @Test
    fun `known complete prefixed replay never touches an edited canonical journal`() =
        fixture { f ->
            val replay = NativeJournalReplayCustody(f.context)
            f.payloads.entries
                .reversed()
                .forEach { (path, file) -> f.capture(replay, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file) }
            assertTrue(replay.finish())
            assertFalse(replay.hasInputs())
            assertEquals("new local edit", f.database.readText())
        }

    @Test
    fun `raw DB before prefixed DB never collapses to an accepted duplicate`() =
        fixture { f ->
            val replay = NativeJournalReplayCustody(f.context)
            replay.capture("database/logdate", 9, "raw other".byteInputStream())
            f.payloads.forEach { (path, file) -> f.capture(replay, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file) }
            assertFalse(replay.finish())
            assertEquals(f.payloads.size + 1, replay.sealedPayloads().size)
            assertEquals("new local edit", f.database.readText())
        }

    @Test
    fun `key-only and changed media remain in custody even beside an old source receipt`() =
        fixture { f ->
            val replay = NativeJournalReplayCustody(f.context)
            replay.capture("files/db_recovery/passphrase_backup", 8, "same key".byteInputStream())
            assertFalse(replay.finish())
            assertTrue(replay.hasInputs())
            val later = NativeJournalReplayCustody(f.context)
            f.payloads.forEach { (path, file) -> f.capture(later, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file) }
            later.capture("files/media/original.png", 9, "new media".byteInputStream())
            assertFalse(later.finish())
            assertEquals(
                "new media",
                later
                    .sealedPayloads()
                    .single { it.path == "files/media/original.png" }
                    .file
                    .readText(),
            )
            assertEquals(
                "same key",
                later
                    .sealedPayloads()
                    .single { it.path == "files/db_recovery/passphrase_backup" }
                    .file
                    .readText(),
            )
        }

    @Test
    fun `merged source retirement removes exact source cohorts but keeps unaccepted key and media`() =
        fixture { f ->
            f.payloads.forEach { (path, file) ->
                f.capture(NativeJournalReplayCustody(f.context), NATIVE_JOURNAL_TRANSFER_PREFIX + path, file)
            }
            val key = File(f.root, "source-key").apply { writeText("sender key") }
            val media = File(f.root, "source-media").apply { writeText("original media") }
            f.capture(NativeJournalReplayCustody(f.context), NATIVE_JOURNAL_TRANSFER_PREFIX + "files/db_recovery/passphrase_backup", key)
            f.capture(NativeJournalReplayCustody(f.context), NATIVE_JOURNAL_TRANSFER_PREFIX + "files/media/original.jpg", media)
            val replay = NativeJournalReplayCustody(f.context)
            val accepted = verifiedNativeJournalSources(replay.sealedPayloads()).values.single()
            val receipt = retainedJournalChecksum(accepted.getValue("source_scope.json"))

            replay.acknowledgeMergedSource("0".repeat(64), accepted)
            assertEquals(5, replay.sealedPayloads().size)
            val bound =
                accepted
                    .mapKeys { (path, _) -> if (path == "source_scope.json") NATIVE_SOURCE_RECEIPT_PATH else path }
                    .mapValues { (_, file) -> setOf(file.canonicalPath) }
            replay.acknowledgeMergedSource(receipt, accepted, bound)
            assertEquals(
                setOf("files/db_recovery/passphrase_backup", "files/media/original.jpg"),
                replay.sealedPayloads().map { it.path.removePrefix(NATIVE_JOURNAL_TRANSFER_PREFIX) }.toSet(),
            )
        }

    @Test
    fun `identical later database callback remains for its own receipt`() =
        fixture { f ->
            val original = NativeJournalReplayCustody(f.context)
            f.payloads.forEach { (path, file) -> f.capture(original, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file) }
            val originalPaths = original.sealedPayloads().mapTo(mutableSetOf()) { it.file.canonicalPath }
            val later = NativeJournalReplayCustody(f.context)
            f.capture(later, NATIVE_JOURNAL_TRANSFER_PREFIX + "database/logdate", f.payloads.getValue("database/logdate"))
            val all = later.sealedPayloads()
            val accepted = verifiedNativeJournalSources(all).values.single()
            val receipt = retainedJournalChecksum(accepted.getValue("source_scope.json"))
            val laterDatabase =
                all.single {
                    it.path == NATIVE_JOURNAL_TRANSFER_PREFIX + "database/logdate" &&
                        it.file.canonicalPath !in originalPaths
                }

            later.acknowledgeMergedSource(receipt, accepted)

            assertEquals(listOf(laterDatabase.file.canonicalPath), later.sealedPayloads().map { it.file.canonicalPath })
        }

    @Test
    fun `merged source retirement preserves a cohort mixed with pending media`() =
        fixture { f ->
            val mixed = NativeJournalReplayCustody(f.context)
            val media = File(f.root, "mixed-source-media").apply { writeText("new original") }
            f.payloads.forEach { (path, file) -> f.capture(mixed, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file) }
            f.capture(mixed, NATIVE_JOURNAL_TRANSFER_PREFIX + "files/media/original.jpg", media)
            val replay = NativeJournalReplayCustody(f.context)
            val accepted = verifiedNativeJournalSources(replay.sealedPayloads()).values.single()
            replay.acknowledgeMergedSource(retainedJournalChecksum(accepted.getValue("source_scope.json")), accepted)
            assertEquals(f.payloads.size + 1, replay.sealedPayloads().size)
            assertTrue(replay.hasInputs())
        }

    @Test
    fun `canonical replay retires mixed core and receipt bound key after durable merge`() =
        fixture { f ->
            val key = File(f.root, "source-key").apply { writeText("sender key") }
            val replay = NativeJournalReplayCustody(f.context)
            f.payloads.forEach { (path, file) -> f.capture(replay, NATIVE_JOURNAL_TRANSFER_PREFIX + path, file) }
            f.capture(replay, NATIVE_JOURNAL_TRANSFER_PREFIX + "files/db_recovery/passphrase_backup", key)
            val accepted = verifiedNativeJournalSources(replay.sealedPayloads()).values.single()
            val receipt = retainedJournalChecksum(accepted.getValue("source_scope.json"))
            val keyPayload = replay.sealedPayloads().single { it.path.endsWith("passphrase_backup") }

            replay.acknowledgeMergedSource(receipt, accepted)
            assertEquals(f.payloads.size + 1, replay.sealedPayloads().size)
            val sourceHashes = f.payloads.mapValues { retainedJournalChecksum(it.value) }
            replay.acknowledgeStagedAuxiliary(setOf(keyPayload.file.canonicalPath), sourceHashes)

            assertFalse(replay.hasInputs())
        }

    @Test
    fun `verified late media retires separately while unfamiliar callback remains guarded`() =
        fixture { f ->
            val replay = NativeJournalReplayCustody(f.context)
            replay.capture("files/media/objects/sha256/ab/${"ab".repeat(32)}.jpg", 5, "image".byteInputStream())
            replay.capture("files/unknown/other", 7, "unknown".byteInputStream())
            val media = replay.sealedPayloads().single { it.path.startsWith("files/media/objects/") }

            replay.acknowledgeRecoveredMedia(setOf(media.file.canonicalPath))

            assertEquals(listOf("files/unknown/other"), replay.sealedPayloads().map { it.path })
            assertTrue(replay.hasInputs())
        }

    @Test
    fun `verified legacy late media retires separately from another pending callback`() =
        fixture { f ->
            val replay = NativeJournalReplayCustody(f.context)
            replay.capture("files/user_media/old.jpg", 5, "image".byteInputStream())
            replay.capture("files/audio_notes/other.m4a", 5, "audio".byteInputStream())
            val verified = replay.sealedPayloads().single { it.path == "files/user_media/old.jpg" }

            replay.acknowledgeRecoveredMedia(setOf(verified.file.canonicalPath))

            assertEquals(listOf("files/audio_notes/other.m4a"), replay.sealedPayloads().map { it.path })
        }

    @Test
    fun `staged auxiliary retirement requires exact physical proof and leaves unknown custody`() =
        fixture { f ->
            val key = File(f.root, "later-key").apply { writeText("sender key") }
            f.capture(NativeJournalReplayCustody(f.context), "files/db_recovery/passphrase_backup", key)
            f.capture(NativeJournalReplayCustody(f.context), NATIVE_JOURNAL_TRANSFER_PREFIX + "sender_owner", key)
            f.capture(NativeJournalReplayCustody(f.context), NATIVE_MEDIA_INVENTORY_PATH, key)
            f.capture(NativeJournalReplayCustody(f.context), "files/unknown/revision", key)
            val replay = NativeJournalReplayCustody(f.context)
            val auxiliary = replay.sealedPayloads().filter { it.path != "files/unknown/revision" }

            replay.acknowledgeStagedAuxiliary(
                setOf(auxiliary.single { it.path == "files/db_recovery/passphrase_backup" }.file.canonicalPath),
            )
            assertEquals(3, replay.sealedPayloads().size)
            replay.acknowledgeStagedAuxiliary(auxiliary.mapTo(mutableSetOf()) { it.file.canonicalPath })

            assertEquals(listOf("files/unknown/revision"), replay.sealedPayloads().map { it.path })
            assertTrue(replay.hasInputs())
        }

    private fun fixture(block: (Fixture) -> Unit) {
        Fixture().use(block)
    }

    private class Fixture : AutoCloseable {
        val root = Files.createTempDirectory("native-replay-custody-").toFile()
        val context = mockk<Context>()
        val database =
            File(root, "database/logdate").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("new local edit")
            }
        val payloads =
            buildMap {
                val sourceDatabase = File(root, "source-db").apply { writeText("older source DB") }
                val sourcePreferences = File(root, "source-preferences").apply { writeText("sender preferences") }
                val sourceReceipt = File(root, "source-receipt")
                writeNativeJournalSourceReceipt(
                    sourceReceipt,
                    mutablePreferencesOf(
                        stringPreferencesKey("identity.canonical_owner_id") to "347c6231-19f4-49fa-bd1d-58ed34b9be03",
                        stringPreferencesKey("backend_url") to "https://cloud.example",
                    ),
                    sourcePreferences,
                    mapOf("database/logdate" to sourceDatabase),
                )
                put("database/logdate", sourceDatabase)
                put("files/logdate.preferences_pb", sourcePreferences)
                put(NATIVE_SOURCE_RECEIPT_PATH, sourceReceipt)
            }

        init {
            every { context.noBackupFilesDir } returns File(root, "no-backup").apply { mkdirs() }
            every { context.filesDir } returns File(root, "files").apply { mkdirs() }
            every { context.getDatabasePath(any()) } answers { File(root, "database/${firstArg<String>()}") }
            val authority = NativeJournalRestoreAuthority(context)
            // Establish the same private intent/tombstone that a successful first installation writes.
            database.delete()
            authority.begin(resumeInterrupted = false)
            val receipt = retainedJournalChecksum(payloads.getValue(NATIVE_SOURCE_RECEIPT_PATH))
            authority.prepareInstallation(receipt, database, retainedJournalChecksum(payloads.getValue("database/logdate")))
            database.writeText("new local edit")
            authority.rememberCompletedSnapshot(receipt, payloads)
        }

        fun capture(
            replay: NativeJournalReplayCustody,
            path: String,
            file: File,
        ) = file.inputStream().use { replay.capture(path, file.length(), it) }

        override fun close() {
            check(root.deleteRecursively())
        }
    }
}
