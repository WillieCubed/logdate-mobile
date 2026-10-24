package app.logdate.client.data.restore

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.uuid.Uuid

/** Deterministic identity decisions for immutable source rows; persistence is owned by the worker. */
@Serializable
data class RetainedJournalMergeCheckpoint(
    val version: Int = CURRENT_VERSION,
    val entries: Map<String, Entry> = emptyMap(),
) {
    @Serializable
    data class Entry(
        val generationId: String,
        val entityType: String,
        val sourceId: String,
        val sourceSha256: String,
        val targetId: String,
        val targetSha256: String? = null,
        val applied: Boolean = false,
    )

    enum class Decision { INSERT, ALREADY_PRESENT, SOURCE_CHANGED, TARGET_CONFLICT }

    data class Plan(
        val checkpoint: RetainedJournalMergeCheckpoint,
        val key: String,
        val targetId: String?,
        val decision: Decision,
    )

    /**
     * Produces a stable target id without overwriting a working row. The caller must persist the
     * returned checkpoint before writing the row, then mark the entry applied after durable write.
     */
    fun plan(
        generationId: String,
        entityType: String,
        sourceId: String,
        sourceSha256: String,
        currentTargetSha256: String?,
        currentMappedTargetSha256: String? = null,
        expectedTargetSha256: String = sourceSha256,
    ): Plan {
        require(generationId.isNotBlank() && entityType.matches(ENTITY_TYPE))
        require(sourceId.isNotBlank() && sourceSha256.matches(SHA256))
        require(expectedTargetSha256.matches(SHA256))
        require(currentTargetSha256 == null || currentTargetSha256.matches(SHA256))
        require(currentMappedTargetSha256 == null || currentMappedTargetSha256.matches(SHA256))
        val key = "$generationId\u0000$entityType\u0000$sourceId"
        val previous = entries[key]
        if (previous != null && previous.sourceSha256 != sourceSha256) {
            return Plan(this, key, null, Decision.SOURCE_CHANGED)
        }
        if (previous?.applied == true) {
            // A stable variant remains the target even if the original ID later becomes available.
            // Rebinding after an applied merge could split child rows across two copies.
            return Plan(this, key, previous.targetId, Decision.ALREADY_PRESENT)
        }
        if (currentTargetSha256 == sourceSha256 || currentTargetSha256 == expectedTargetSha256) {
            val retained = Entry(generationId, entityType, sourceId, sourceSha256, sourceId, sourceSha256, applied = true)
            return Plan(copy(entries = entries + (key to retained)), key, sourceId, Decision.ALREADY_PRESENT)
        }
        if (previous?.targetId == sourceId &&
            currentTargetSha256 != null &&
            currentTargetSha256 == previous.targetSha256
        ) {
            return Plan(copy(entries = entries + (key to previous.copy(applied = true))), key, sourceId, Decision.ALREADY_PRESENT)
        }
        val chosen =
            previous ?: Entry(
                generationId = generationId,
                entityType = entityType,
                sourceId = sourceId,
                sourceSha256 = sourceSha256,
                targetId = if (currentTargetSha256 == null) sourceId else stableVariantId(generationId, entityType, sourceId),
                targetSha256 = expectedTargetSha256,
            )
        val current = currentTargetSha256
        val target = chosen.targetId
        if (current != null && target == sourceId) {
            val variant = stableVariantId(generationId, entityType, sourceId)
            val replacement = chosen.copy(targetId = variant)
            return Plan(copy(entries = entries + (key to replacement)), key, variant, Decision.INSERT)
        }
        if (target != sourceId && currentMappedTargetSha256 != null) {
            if (currentMappedTargetSha256 == chosen.targetSha256) {
                return Plan(copy(entries = entries + (key to chosen.copy(applied = true))), key, target, Decision.ALREADY_PRESENT)
            }
            return Plan(this, key, null, Decision.TARGET_CONFLICT)
        }
        return Plan(copy(entries = entries + (key to chosen)), key, target, Decision.INSERT)
    }

    fun markApplied(
        key: String,
        targetSha256: String? = null,
    ): RetainedJournalMergeCheckpoint {
        val entry = entries[key] ?: error("Merge identity must be persisted before applying a row")
        require(targetSha256 == null || targetSha256.matches(SHA256))
        return copy(entries = entries + (key to entry.copy(targetSha256 = targetSha256 ?: entry.targetSha256, applied = true)))
    }

    companion object {
        const val CURRENT_VERSION = 1
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val ENTITY_TYPE = Regex("[a-z][a-z0-9_]{0,31}")
        private const val NAMESPACE = "eaa1eeb7-9d69-5d19-a8d8-88f7dd1328d1"

        fun empty() = RetainedJournalMergeCheckpoint()

        /** RFC 4122 UUIDv5 with a fixed namespace and the retained generation as part of the name. */
        internal fun stableVariantId(
            generationId: String,
            entityType: String,
            sourceId: String,
        ): String {
            val name = "$generationId\u0000$entityType\u0000$sourceId".toByteArray(StandardCharsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-1")
            val namespaceBytes = uuidBytes(NAMESPACE)
            val bytes = digest.digest(namespaceBytes + name).copyOf(16)
            bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x50).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
            val encoded =
                buildString(36) {
                    bytes.forEachIndexed { index, byte ->
                        if (index == 4 || index == 6 || index == 8 || index == 10) append('-')
                        val value = byte.toInt() and 0xff
                        append(HEX[value ushr 4])
                        append(HEX[value and 0x0f])
                    }
                }
            Uuid.parse(encoded)
            return encoded
        }

        private fun uuidBytes(value: String): ByteArray {
            val hex = value.filterNot { it == '-' }
            require(hex.length == 32 && hex.all { it in '0'..'9' || it in 'a'..'f' })
            return ByteArray(16) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }

        private const val HEX = "0123456789abcdef"
    }
}
