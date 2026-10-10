package app.logdate.client.database.entities

import kotlin.uuid.Uuid

/** A journal's id and its cover image reference, for rewriting references in place. */
data class JournalCoverUri(
    val id: Uuid,
    val coverImageUri: String,
)
