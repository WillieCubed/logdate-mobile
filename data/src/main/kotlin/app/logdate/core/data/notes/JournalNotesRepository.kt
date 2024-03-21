package app.logdate.core.data.notes

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface JournalNotesRepository {
    val allNotesObserved: Flow<List<JournalNote>>

    fun observeNotesInJournal(journalId: String): Flow<List<JournalNote>>

    suspend fun create(note: String, journalId: String)

    suspend fun removeFromJournal(noteId: String, journalId: String)
}

enum class NoteType {
    TEXT,
    AUDIO,
    IMAGE,
    VIDEO,
    LOCATION,
}

//data class ImageMetadata

/**
 * A generic container for user-added content.
 *
 * This corresponds to
 */
sealed class JournalNote(
    val type: NoteType,
) {
    abstract val uid: String
    abstract val creationTimestamp: Instant
    abstract val lastUpdated: Instant

    /**
     * A text note, like a unit of content on a microblog (e.g. post, tweet).
     *
     * Text notes can constitute simple statements or
     */
    data class Text(
        val content: String,
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
    ) : JournalNote(NoteType.TEXT)

    data class Image(
        val mediaRef: String,
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
    ) : JournalNote(NoteType.IMAGE)

    data class Video(
        val mediaRef: String,
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
    ) : JournalNote(NoteType.VIDEO)

    data class Audio(
        val mediaRef: String,
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
    ) : JournalNote(NoteType.AUDIO)
}