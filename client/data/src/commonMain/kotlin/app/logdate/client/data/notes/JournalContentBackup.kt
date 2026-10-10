package app.logdate.client.data.notes

import app.logdate.client.repository.journals.JournalNote
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class JournalContentBackup(
    val notes: List<JournalNote>,
    @Serializable(with = UuidToUuidListMapSerializer::class)
    val journalToNotesMap: Map<Uuid, List<Uuid>> = emptyMap(),
    val generated: Instant = Clock.System.now(),
    val version: String = "1.0",
)
