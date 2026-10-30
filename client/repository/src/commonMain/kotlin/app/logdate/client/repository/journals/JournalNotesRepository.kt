@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.logdate.client.repository.journals

import app.logdate.client.repository.transcription.TranscriptDocument
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.PhotoPresentation
import app.logdate.util.UuidSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** How many notes exist, and how many of them are photos, videos or recordings. */
data class NoteCounts(
    val total: Int,
    val withMedia: Int,
)

interface JournalNotesRepository {
    val allNotesObserved: Flow<List<JournalNote>>

    fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>>

    /**
     * Returns all journal-to-note content links as (journalId, noteId) pairs.
     *
     * Intended for bulk operations like export where querying per-journal is wasteful.
     */
    suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>>

    fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>>

    /**
     * Observes one note, emitting null while it does not exist.
     *
     * Prefer this to filtering [allNotesObserved]: it reads only that note and changes only when it does.
     */
    fun observeNoteById(noteId: Uuid): Flow<JournalNote?> =
        allNotesObserved.map { notes -> notes.firstOrNull { it.uid == noteId } }.distinctUntilChanged()

    /**
     * Observes one page of notes, newest first: the [pageSize] notes after the newest [offset].
     */
    fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>>

    /**
     * Observes the newest [pageSize] notes, emitting again whenever any of them changes.
     */
    fun observeNotesStream(pageSize: Int = 50): Flow<List<JournalNote>>

    /**
     * Fast query for recent notes to enable immediate timeline display.
     */
    fun observeRecentNotes(limit: Int = 20): Flow<List<JournalNote>>

    /**
     * Observes the newest audio notes only, newest first.
     *
     * A limit on [observeRecentNotes] counts every note type, so a busy day of text notes can push
     * recordings out of a list built from it.
     */
    fun observeRecentAudioNotes(limit: Int = 20): Flow<List<JournalNote.Audio>> =
        allNotesObserved.map { notes ->
            notes
                .filterIsInstance<JournalNote.Audio>()
                .sortedByDescending(JournalNote::creationTimestamp)
                .take(limit)
        }

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
     * Returns the subset of [paths] that at least one note still references.
     *
     * Intended for callers that only need to know whether a handful of candidate media
     * paths are still owned by a note -- e.g. deciding which files a discarded draft can
     * safely delete -- without loading and mapping every note in the app.
     *
     * The default implementation still scans [allNotesObserved]; an implementation backed
     * by an indexed store should override this with a targeted query instead.
     */
    suspend fun notesReferencingMediaPaths(paths: Set<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        return allNotesObserved
            .first()
            .mapNotNull { it.mediaRefOrNull() }
            .filterTo(mutableSetOf()) { it in paths }
    }

    /**
     * Fetches all notes for a single calendar day.
     */
    suspend fun getNotesForDay(day: LocalDate): List<JournalNote> = observeNotesForDay(day).first()

    /**
     * Returns the set of dates that have at least one entry within the given range.
     * Used for streak calculation without loading full note contents.
     */
    suspend fun getDatesWithEntries(
        start: LocalDate,
        end: LocalDate,
    ): Set<LocalDate> {
        val timezone = TimeZone.currentSystemDefault()
        val startInstant = start.atStartOfDayIn(timezone)
        val endInstant = end.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timezone)
        return observeNotesInRange(startInstant, endInstant)
            .first()
            .map { it.creationTimestamp.toLocalDateTime(timezone).date }
            .toSet()
    }

    /**
     * Observes when every note was created, across all note types.
     *
     * Used by streak calculation, which needs every entry's creation time across all of history
     * but none of its content. The default derives it from [allNotesObserved]; implementations
     * backed by a database should read the creation column directly instead.
     */
    fun observeEntryTimestamps(): Flow<List<Instant>> = allNotesObserved.map { notes -> notes.map { it.creationTimestamp } }

    /**
     * Fetches a specific note by its ID. Used for loading entries for editing in new windows.
     *
     * @param noteId The unique identifier of the note to fetch
     * @return The note if found, null otherwise
     */
    suspend fun getNoteById(noteId: Uuid): JournalNote?

    /** Counts every note and the notes that carry media, without loading them. */
    suspend fun countNotes(): NoteCounts =
        allNotesObserved.first().let { notes ->
            val media = notes.count { it is JournalNote.Image || it is JournalNote.Audio || it is JournalNote.Video }
            NoteCounts(total = notes.size, withMedia = media)
        }

    /** Fetches several notes at once. IDs without a note are absent from the result. */
    suspend fun getNotesByIds(noteIds: Set<Uuid>): Map<Uuid, JournalNote> =
        noteIds.mapNotNull { id -> getNoteById(id)?.let { id to it } }.toMap()

    /** Fills missing audio metadata only while the same live note still owns [mediaRef]. */
    suspend fun updateAudioDurationIfUnknown(
        noteId: Uuid,
        mediaRef: String,
        durationMs: Long,
    ): Boolean = false

    /**
     * Creates a new note.
     */
    suspend fun create(note: JournalNote): Uuid

    suspend fun getNoteRecoveryScope(): JournalMergeScope? = null

    /** Restores a captured note without deleting its media or journal memberships. */
    suspend fun compareAndSetNoteForRestore(
        expected: JournalNote?,
        replacement: JournalNote,
        scope: JournalMergeScope? = null,
        recoveryId: String? = null,
    ): Boolean {
        if (expected != null) return false
        create(replacement)
        return true
    }

    /** Automatic retained-source replay never reverses a settled local deletion. */
    suspend fun compareAndSetNoteForRetainedRestore(
        sourceId: Uuid,
        expected: JournalNote?,
        replacement: JournalNote,
        scope: JournalMergeScope? = null,
        recoveryId: String? = null,
    ): Boolean = false

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
    HANDWRITING,
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
 *
 * A note stores only the place's [id]. The name and center are known when the place itself is on
 * this device or when a record carries a copy, and absent otherwise; absent means unknown, never an
 * empty name or a center of 0, 0. A center is either both coordinates or neither.
 */
@Serializable
data class NotePlace(
    @Serializable(with = UuidSerializer::class)
    val id: Uuid,
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    init {
        require((latitude == null) == (longitude == null)) { "A place's center needs both a latitude and a longitude" }
    }
}

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
     * IANA id of the time zone the note was captured in, or null when it was not recorded (notes
     * written before the zone was captured, or received from a device that did not send one).
     * Kept as an opaque string; parse it with a failure-tolerant lookup, since a zone added to the
     * tz database after this device's release does not resolve here.
     */
    abstract val timeZoneId: String?

    /**
     * The device the note was captured on, or null when it was not recorded: notes from before this
     * was tracked, and notes written on this device, which does not record itself yet. Set once where
     * the note is captured and never edited; no copy of the note may erase it.
     */
    abstract val capturedOn: CaptureDevice?

    /**
     * What the person was doing when the note was captured, or null when nothing was recorded:
     * activity awareness was off, nothing was under way, or the note predates this. Set once where the
     * note is captured and never edited; no copy of the note may erase it. Left out of the note's JSON
     * while null, so notes without it encode as they did before it existed.
     */
    abstract val capturedDuring: CaptureActivity?

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
        override val timeZoneId: String? = null,
        @Serializable(with = LenientCaptureDeviceSerializer::class)
        override val capturedOn: CaptureDevice? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @Serializable(with = LenientCaptureActivitySerializer::class)
        override val capturedDuring: CaptureActivity? = null,
    ) : JournalNote(NoteType.TEXT)

    @Serializable
    data class Handwriting(
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val document: HandwritingDocument,
        /** Original stored bytes when this client cannot read the document. Never replace them with an empty page. */
        val unreadableContent: String? = null,
        override val syncVersion: Long = 0,
        override val location: NoteLocation? = null,
        override val timeZoneId: String? = null,
        @Serializable(with = LenientCaptureDeviceSerializer::class)
        override val capturedOn: CaptureDevice? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @Serializable(with = LenientCaptureActivitySerializer::class)
        override val capturedDuring: CaptureActivity? = null,
    ) : JournalNote(NoteType.HANDWRITING)

    @Serializable
    data class Image(
        @Serializable(with = UuidSerializer::class)
        override val uid: Uuid = Uuid.random(),
        override val creationTimestamp: Instant,
        override val lastUpdated: Instant,
        val mediaRef: String,
        val caption: String = "",
        val presentation: PhotoPresentation = PhotoPresentation.EdgeToEdge,
        override val syncVersion: Long = 0,
        override val location: NoteLocation? = null,
        override val timeZoneId: String? = null,
        @Serializable(with = LenientCaptureDeviceSerializer::class)
        override val capturedOn: CaptureDevice? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @Serializable(with = LenientCaptureActivitySerializer::class)
        override val capturedDuring: CaptureActivity? = null,
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
        override val timeZoneId: String? = null,
        @Serializable(with = LenientCaptureDeviceSerializer::class)
        override val capturedOn: CaptureDevice? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @Serializable(with = LenientCaptureActivitySerializer::class)
        override val capturedDuring: CaptureActivity? = null,
        /**
         * Length of the video in milliseconds, or null when it isn't known. Left out of the note's JSON
         * while null, so notes without it encode as they did before it existed.
         */
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val durationMs: Long? = null,
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
        override val timeZoneId: String? = null,
        val caption: String = "",
        val transcription: String = "",
        val transcript: TranscriptDocument? = null,
        @Serializable(with = LenientCaptureDeviceSerializer::class)
        override val capturedOn: CaptureDevice? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        @Serializable(with = LenientCaptureActivitySerializer::class)
        override val capturedDuring: CaptureActivity? = null,
    ) : JournalNote(NoteType.AUDIO)
}

/** The media file this note owns, or `null` for note types that don't carry media. */
fun JournalNote.mediaRefOrNull(): String? =
    when (this) {
        is JournalNote.Audio -> mediaRef
        is JournalNote.Image -> mediaRef
        is JournalNote.Video -> mediaRef
        is JournalNote.Text, is JournalNote.Handwriting -> null
    }

/** A copy of this note with [timeZoneId] as its capture time zone; everything else is unchanged. */
fun JournalNote.withTimeZoneId(timeZoneId: String?): JournalNote =
    when (this) {
        is JournalNote.Text -> copy(timeZoneId = timeZoneId)
        is JournalNote.Handwriting -> copy(timeZoneId = timeZoneId)
        is JournalNote.Image -> copy(timeZoneId = timeZoneId)
        is JournalNote.Video -> copy(timeZoneId = timeZoneId)
        is JournalNote.Audio -> copy(timeZoneId = timeZoneId)
    }
