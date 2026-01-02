package app.logdate.client.sync.metadata

import kotlin.uuid.Uuid

/**
 * Stable encoding for association identifiers stored in the sync outbox.
 */
data class AssociationPendingKey(
    val journalId: Uuid,
    val contentId: Uuid
) {
    fun toPendingId(): String = "${journalId}::${contentId}"

    companion object {
        fun fromPendingId(value: String): AssociationPendingKey? {
            val parts = value.split("::")
            if (parts.size != 2) return null
            return try {
                AssociationPendingKey(Uuid.parse(parts[0]), Uuid.parse(parts[1]))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
