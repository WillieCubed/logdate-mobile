package app.logdate.client.data.restore

import app.logdate.client.device.restore.RetainedJournalWorkspace
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class RetainedJournalIncomingCandidatesTest {
    private val original = "complete original".encodeToByteArray()
    private val digest = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }
    private val mediaPath = "files/media/objects/sha256/${digest.take(2)}/$digest.png"
    private val reference = "logdate-media://library/objects/sha256/${digest.take(2)}/$digest.png"
    private val keyPath = "files/db_recovery/passphrase_backup"

    private fun fixture(block: (File, RetainedJournalWorkspace) -> Unit) {
        val root = Files.createTempDirectory("retained-independent-inputs-").toFile()
        val database =
            File(root, "source/logdate").apply {
                parentFile!!.mkdirs()
                writeText("encrypted retained journal")
            }
        try {
            block(root, workspace(root, database).also { it.prepare() })
        } finally {
            check(root.deleteRecursively())
        }
    }

    private fun workspace(
        root: File,
        database: File,
    ) = RetainedJournalWorkspace(
        File(root, "session.json"),
        File(root, "retained"),
        { mapOf("database/logdate" to database) },
        setOf("database/logdate"),
        File(root, "no-backup/authority"),
    )

    private fun capture(
        store: RetainedJournalWorkspace,
        id: String,
        path: String,
        bytes: ByteArray,
    ) = store.captureIncomingFile(id, path, bytes.size.toLong(), bytes.inputStream())

    @Test fun restartSealsMixedPartialDeliveryAndReusesCompleteKeyAndCanonicalOriginal() =
        fixture { root, store ->
            val id = store.beginIncomingDelivery(Uuid.random().toString())
            capture(store, id, keyPath, "correct-key-candidate".encodeToByteArray())
            capture(store, id, mediaPath, original)
            assertFailsWith<IOException> { store.captureIncomingFile(id, "files/media/unrelated.png", 100, "partial".byteInputStream()) }
            val restarted = workspace(root, File(root, "source/logdate"))
            restarted.resume()
            // Exactly the production worker boundary: native gate excludes active callbacks.
            restarted.finishIncomingDelivery()
            assertTrue(restarted.coherentIncomingPayloads().isEmpty())
            val candidates = retainedIndependentIncomingCandidates(restarted.incomingPayloads().values.flatten())
            assertEquals(2, candidates.size)
            assertEquals("correct-key-candidate", candidates.single { keyPath in it }.getValue(keyPath).readText())
            assertNotNull(RetainedManagedMediaSource(emptyMap(), candidates).resolve(reference))
            assertEquals(
                "encrypted retained journal",
                restarted
                    .retainedSources()
                    .values
                    .single()
                    .getValue("database/logdate")
                    .readText(),
            )
        }

    @Test fun ambiguousKeyVariantsRemainSeparateActualDecryptCandidates() =
        fixture { _, store ->
            val id = store.beginIncomingDelivery(Uuid.random().toString())
            capture(store, id, keyPath, "wrong-key".encodeToByteArray())
            capture(store, id, keyPath, "right-key".encodeToByteArray())
            store.finishIncomingDelivery()
            assertTrue(store.coherentIncomingPayloads().isEmpty())
            val candidates = retainedIndependentIncomingCandidates(store.incomingPayloads().values.flatten())
            assertEquals(2, candidates.size)
            assertEquals(setOf("wrong-key", "right-key"), candidates.map { it.getValue(keyPath).readText() }.toSet())
        }

    @Test fun ambiguousDatabaseAndWalNeverBecomeIndependentCohorts() =
        fixture { _, store ->
            val id = store.beginIncomingDelivery(Uuid.random().toString())
            capture(store, id, "database/logdate", "first".encodeToByteArray())
            capture(store, id, "database/logdate", "other".encodeToByteArray())
            capture(store, id, "database/logdate-wal", "wal".encodeToByteArray())
            store.finishIncomingDelivery()
            assertTrue(store.coherentIncomingPayloads().isEmpty())
            assertTrue(retainedIndependentIncomingCandidates(store.incomingPayloads().values.flatten()).isEmpty())
        }

    @Test fun canonicalDigestSelectsVerifiedBytesAcrossVariantsWhileLegacyPathsStayExcluded() =
        fixture { _, store ->
            val id = store.beginIncomingDelivery(Uuid.random().toString())
            capture(store, id, mediaPath, "incorrect bytes".encodeToByteArray())
            capture(store, id, mediaPath, original)
            capture(store, id, "files/media/legacy.png", original)
            store.finishIncomingDelivery()
            val candidates = retainedIndependentIncomingCandidates(store.incomingPayloads().values.flatten())
            assertEquals(2, candidates.size)
            val source = RetainedManagedMediaSource(emptyMap(), candidates)
            assertEquals(original.toList(), assertNotNull(source.resolve(reference)).file.readBytes().toList())
            assertNull(source.resolve("logdate-media://library/legacy.png"))
        }

    @Test fun truncatedKeysAndPartFilesAreNeverCandidates() =
        fixture { _, store ->
            val id = store.beginIncomingDelivery(Uuid.random().toString())
            assertFailsWith<IOException> { store.captureIncomingFile(id, keyPath, 100, "partial".byteInputStream()) }
            capture(store, id, keyPath + ".part", "uncommitted".encodeToByteArray())
            store.finishIncomingDelivery()
            assertTrue(retainedIndependentIncomingCandidates(store.incomingPayloads().values.flatten()).isEmpty())
        }

    @Test fun corruptionCannotBePromotedThroughIndependentSelection() =
        fixture { _, store ->
            val id = store.beginIncomingDelivery(Uuid.random().toString())
            capture(store, id, keyPath, "key".encodeToByteArray())
            store.finishIncomingDelivery()
            store
                .incomingPayloads()
                .getValue(id)
                .single()
                .file
                .appendText("corrupt")
            assertFailsWith<IllegalStateException> { retainedIndependentIncomingCandidates(store.incomingPayloads().values.flatten()) }
        }
}
