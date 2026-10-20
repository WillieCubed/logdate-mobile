package app.logdate.client.repository.assistants

import kotlin.uuid.Uuid

/** A fixed selection; subsequently created journals are never implicitly included. */
data class AssistantContentScope(
    val journalIds: Set<Uuid>,
    val excludedEntryIds: Set<Uuid> = emptySet(),
    val includeUnfiled: Boolean = false,
) {
    override fun toString(): String = "AssistantContentScope([redacted])"
}
