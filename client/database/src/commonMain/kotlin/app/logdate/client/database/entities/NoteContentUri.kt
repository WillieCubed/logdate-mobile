package app.logdate.client.database.entities

import kotlin.uuid.Uuid

/** A media note's id and the media reference it stores, for rewriting references in place. */
data class NoteContentUri(
    val uid: Uuid,
    val contentUri: String,
)
