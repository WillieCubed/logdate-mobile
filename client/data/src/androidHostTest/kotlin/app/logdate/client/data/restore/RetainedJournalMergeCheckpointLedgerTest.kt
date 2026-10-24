package app.logdate.client.data.restore

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedJournalMergeCheckpointLedgerTest {
    @Test
    fun `large cumulative checkpoint ledger remains readable and retry advances one entry`() {
        val storage = MemoryCheckpointRecords()
        val ledger = RetainedJournalMergeCheckpointLedger(storage)
        val expectedCount = 30_000

        repeat(expectedCount) { index ->
            val sourceId = "source-${index.toString().padStart(5, '0')}"
            val entry =
                RetainedJournalMergeCheckpoint.Entry(
                    generationId = GENERATION,
                    entityType = "note_image",
                    sourceId = sourceId,
                    sourceSha256 = HASH,
                    targetId = sourceId,
                    targetSha256 = HASH,
                    applied = true,
                )
            ledger.write(key(sourceId), entry)
        }

        assertTrue(storage.totalBytes > 8 * 1024 * 1024)
        assertTrue(ledger.allApplied(GENERATION))

        val pendingId = "source-retry"
        ledger.write(
            key(pendingId),
            RetainedJournalMergeCheckpoint.Entry(GENERATION, "note_image", pendingId, HASH, pendingId, HASH, applied = false),
        )
        assertFalse(ledger.allApplied(GENERATION))
        ledger.markApplied(key(pendingId), HASH)
        assertTrue(ledger.allApplied(GENERATION))
    }

    private fun key(sourceId: String) = "$GENERATION\u0000note_image\u0000$sourceId"

    private class MemoryCheckpointRecords : CheckpointRecords {
        private val values = mutableMapOf<String, String>()
        var totalBytes = 0
            private set

        override fun read(name: String): String? = values[name]

        override fun writeAtomically(
            name: String,
            value: String,
        ) {
            val previous = values.put(name, value)
            totalBytes += value.toByteArray(Charsets.UTF_8).size - (previous?.toByteArray(Charsets.UTF_8)?.size ?: 0)
        }

        override fun forEachRecord(visit: (String) -> Boolean) {
            for (value in values.values) {
                if (!visit(value)) return
            }
        }
    }

    private companion object {
        const val GENERATION = "generation-a"
        val HASH = "1".repeat(64)
    }
}
