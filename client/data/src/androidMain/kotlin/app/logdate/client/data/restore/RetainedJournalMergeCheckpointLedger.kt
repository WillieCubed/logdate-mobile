package app.logdate.client.data.restore

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Persistence-independent checkpoint logic; the Android adapter supplies atomic private files. */
internal class RetainedJournalMergeCheckpointLedger(
    private val records: CheckpointRecords,
) {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }

    fun read(key: String): RetainedJournalMergeCheckpoint.Entry? =
        records
            .read(recordName(key))
            ?.also { encoded ->
                require(
                    encoded.toByteArray(Charsets.UTF_8).size in 1..MAX_ENTRY_BYTES,
                ) { "Recovery checkpoint entry exceeds its size limit" }
            }?.let { encoded ->
                json.decodeFromString<RetainedJournalMergeCheckpoint.Entry>(encoded).also {
                    require(it.generationId.isNotBlank() && it.sourceId.isNotBlank()) { "Invalid recovery checkpoint identity" }
                }
            }

    fun write(
        key: String,
        entry: RetainedJournalMergeCheckpoint.Entry,
    ) {
        require(key == "${entry.generationId}\u0000${entry.entityType}\u0000${entry.sourceId}")
        val encoded = json.encodeToString(entry)
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_ENTRY_BYTES) { "Recovery checkpoint entry exceeds its size limit" }
        records.writeAtomically(recordName(key), encoded)
    }

    fun markApplied(
        key: String,
        targetSha256: String,
    ) {
        val entry = read(key) ?: error("Merge identity must be persisted before applying a row")
        write(key, entry.copy(targetSha256 = targetSha256, applied = true))
    }

    fun allApplied(generationId: String): Boolean {
        var allApplied = true
        records.forEachRecord { encoded ->
            require(encoded.toByteArray(Charsets.UTF_8).size in 1..MAX_ENTRY_BYTES) { "Recovery checkpoint entry exceeds its size limit" }
            val entry = json.decodeFromString<RetainedJournalMergeCheckpoint.Entry>(encoded)
            if (entry.generationId == generationId && !entry.applied) {
                allApplied = false
                false
            } else {
                true
            }
        }
        return allApplied
    }

    private fun recordName(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2 + CHECKPOINT_SUFFIX.length) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
            append(CHECKPOINT_SUFFIX)
        }
    }

    companion object {
        internal const val MAX_ENTRY_BYTES = 16 * 1024
        internal const val CHECKPOINT_SUFFIX = ".checkpoint.json"
        private const val HEX = "0123456789abcdef"
    }
}

/** A single bounded record is replaced atomically; enumeration must not materialize all records. */
internal interface CheckpointRecords {
    fun read(name: String): String?

    fun writeAtomically(
        name: String,
        value: String,
    )

    fun forEachRecord(visit: (String) -> Boolean)
}
