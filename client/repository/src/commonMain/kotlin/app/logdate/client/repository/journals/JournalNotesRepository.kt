package app.logdate.client.repository.journals

import app.logdate.util.UuidSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface JournalNotesRepository {
    val allNotesObserved: Flow<List<JournalNote>>

    fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>>

    fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>>

    /**
     * Observes notes in pages for efficient loading.
     */
    fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>>

    /**
     * Observes notes in a streaming fashion, emitting results as they become available.
     */
    fun observeNotesStream(pageSize: Int = 50): Flow<List<JournalNote>>

    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    fun observeRecentNotes(limit: Int = 20): Flow<List<JournalNote>>

    /**
     * Observes the notes for a single calendar day.
     */
    fun observeNotesForDay(day: LocalDate): Flow<List<JournalNote>> =
        allNotesObserved.map { notes ->
            val timezone = kotlinx.datetime.TimeZone.currentSystemDefault()
            notes
                .filter { note ->
                    note.creationTimestamp
                        .toLocalDateTime(timezone)
                        .date == day
                }.sortedByDescending(JournalNote::creationTimestamp)
        }

    /**
     * Fetches older notes before the provided timestamp, newest first.
     */
    suspend fun getNotesBefore(
        beforeExclusive: Instant,
        limit: Int,
    ): List<JournalNote> =
        allNotesObserved
            .first()
            .asSequence()
            .filter { note -> note.creationTimestamp < beforeExclusive }
            .sortedByDescending(JournalNote::creationTimestamp)
            .take(limit)
            .toList()

    /**
     * Fast existence check for older notes before a timestamp.
     */
    suspend fun hasNotesBefore(beforeExclusive: Instant): Boolean = getNotesBefore(beforeExclusive, limit = 1).isNotEmpty()

    /**
     * Fetches all notes for a single calendar day.
     */
    suspend fun getNotesForDay(day: LocalDate): List<JournalNote> = observeNotesForDay(day).first()

    /**
     * Fetches a specific note by its ID. Used for loading entries for editing in new windows.
     *
     * @param noteId The unique identifier of the note to fetch
     * @return The note if found, null otherwise
     */
    suspend fun getNoteById(noteId: Uuid): JournalNote?

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
    suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    )

    suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    )
}

enum class NoteType {
    TEXT,
    AUDIO,
    IMAGE,
    VIDEO,
    LOCATION,
}

/**
 * Coordinates captured at note creation time.
 */
@Serializable
data class NoteCoordinates(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Float? = null,
)

/**
 * A semantic place reference (e.g., "Home", "Work").
 */
@Serializable
data class NotePlace(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Location data for a note, combining raw coordinates with optional semantic place.
 *
 * The hybrid approach preserves:
 * - Exact GPS coordinates at note creation (coordinates)
 * - Optional semantic meaning for display (place)
 */
@Serializable
data class NoteLocation(
    val coordinates: NoteCoordinates? = null,
    val place: NotePlace? = null,
) {
    val hasLocation: Boolean get() = coordinates != null || place != null
    val displayName: String? get() = place?.name
    val effectiveLatitude: Double? get() = coordinates?.latitude ?: place?.latitude
    val effectiveLongitude: Double? get() = coordinates?.longitude ?: place?.longitude
}

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
    abstract val location: NoteLocation?

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
        override val location: NoteLocation? = null,
    ) : JournalNote(NoteType.TEXT)

    @Serializable
    data class Image(
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val mediaRef: String,
        val caption: String = "",
        override val syncVersion: Long = 0,
        override val location: NoteLocation? = null,
    ) : JournalNote(NoteType.IMAGE)

    @Serializable
    data class Video(
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val mediaRef: String,
        val caption: String = "",
        override val syncVersion: Long = 0,
        override val location: NoteLocation? = null,
    ) : JournalNote(NoteType.VIDEO)

    @Serializable
    data class Audio(
        val mediaRef: String,
        val durationMs: Long = 0,
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        override val syncVersion: Long = 0,
        override val location: NoteLocation? = null,
    ) : JournalNote(NoteType.AUDIO)
}
