package app.logdate.client.repository.assistants

import kotlin.uuid.Uuid

enum class AssistantSourceKind {
    TEXT,
    TRANSCRIPT,
    ORIGINAL_MEDIA,
    UNSUPPORTED,
}

data class AssistantSourceMetadata(
    val id: Uuid,
    val journalIds: Set<Uuid>,
    val kind: AssistantSourceKind,
    val shared: Boolean,
) {
    override fun toString(): String = "AssistantSourceMetadata([redacted])"
}
