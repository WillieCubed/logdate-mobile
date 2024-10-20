package app.logdate.core.data.notes

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

interface JournalNotesRepository {
    val allNotesObserved: Flow<List<JournalNote>>

    fun observeNotesInJournal(journalId: String): Flow<List<JournalNote>>

    fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>>

    /**
     * Creates a new note.
     */
    suspend fun create(note: JournalNote)

    /**
     * Deletes a note.
     */
    suspend fun remove(note: JournalNote)

    /**
     * Deletes a note by its ID.
     */
    suspend fun removeById(noteId: String)

    /**
     * Creates a new note and add it to a journal.
     */
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
 * This corresponds to a log entry in the user's timeline.
 *
 * TODO: Choose a better name for this type.
 */
@Serializable
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
    @Serializable
    data class Text(
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val content: String,
    ) : JournalNote(NoteType.TEXT)

    @Serializable
    data class Image(
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val mediaRef: String,
    ) : JournalNote(NoteType.IMAGE)

    @Serializable
    data class Video(
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val mediaRef: String,
    ) : JournalNote(NoteType.VIDEO)

    @Serializable
    data class Audio(
        val mediaRef: String,
        override val uid: String,
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
    ) : JournalNote(NoteType.AUDIO)
}