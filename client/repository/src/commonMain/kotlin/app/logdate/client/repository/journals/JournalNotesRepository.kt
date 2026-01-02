package app.logdate.client.repository.journals

import app.logdate.util.UuidSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

interface JournalNotesRepository {
    val allNotesObserved: Flow<List<JournalNote>>

    fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>>

    fun observeNotesInRange(start: Instant, end: Instant): Flow<List<JournalNote>>

    /**
     * Observes notes in pages for efficient loading.
     */
    fun observeNotesPage(pageSize: Int, offset: Int): Flow<List<JournalNote>>

    /**
     * Observes notes in a streaming fashion, emitting results as they become available.
     */
    fun observeNotesStream(pageSize: Int = 50): Flow<List<JournalNote>>

    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    fun observeRecentNotes(limit: Int = 20): Flow<List<JournalNote>>

    /**
     * Creates a new note.
     */
    suspend fun create(note: JournalNote): Uuid

    /**
     * Deletes a note.
     */
    suspend fun remove(note: JournalNote)

    /**
     * Deletes a note by its ID.
     */
    suspend fun removeById(noteId: Uuid)

    /**
     * Creates a new note and add it to a journal.
     */
    suspend fun create(note: JournalNote, journalId: Uuid)

    suspend fun removeFromJournal(noteId: Uuid, journalId: Uuid)
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
    @SerialName("noteType")
    val type: NoteType,
) {
    @Serializable(with = UuidSerializer::class)
    abstract val uid: Uuid
    abstract val creationTimestamp: Instant
    abstract val lastUpdated: Instant
    abstract val syncVersion: Long

    /**
     * A text note, like a unit of content on a microblog (e.g. post, tweet).
     *
     * Text notes can constitute simple statements or long form content.
     */
    @Serializable
    data class Text(
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val content: String,
        override val syncVersion: Long = 0,
    ) : JournalNote(NoteType.TEXT)

    @Serializable
    data class Image(
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val mediaRef: String,
        override val syncVersion: Long = 0,
    ) : JournalNote(NoteType.IMAGE)

    @Serializable
    data class Video(
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val mediaRef: String,
        override val syncVersion: Long = 0,
    ) : JournalNote(NoteType.VIDEO)

    @Serializable
    data class Audio(
        val mediaRef: String,
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        override val syncVersion: Long = 0,
    ) : JournalNote(NoteType.AUDIO)
}
