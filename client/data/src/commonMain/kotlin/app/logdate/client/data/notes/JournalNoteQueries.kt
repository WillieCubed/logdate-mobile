package app.logdate.client.data.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.MediaCaptionDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.transcription.TranscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class JournalNoteQueries(
    private val textNoteDao: TextNoteDao,
    private val imageNoteDao: ImageNoteDao,
    private val audioNoteDao: AudioNoteDao,
    private val videoNoteDao: VideoNoteDao,
    private val journalContentDao: JournalContentDao,
    private val mediaCaptionDao: MediaCaptionDao,
    private val notePlaceResolver: NotePlaceResolver,
    private val transcriptionRepository: TranscriptionRepository?,
) {
    val allNotesObserved: Flow<List<JournalNote>> =
        notePlaceResolver
            .observeAll()
            .combine(
                observeNoteBuckets(
                    textFlow = textNoteDao.getAllNotes(),
                    imageFlow = imageNoteDao.getAllNotes(),
                    audioFlow = audioNoteDao.getAllNotes(),
                    videoFlow = videoNoteDao.getAllNotes(),
                ),
            ) { placeLookup, buckets ->
                buckets.toNotes(placeLookup)
            }.combine(mediaCaptionDao.observeAll()) { notes, captions ->
                val captionMap = captions.associate { it.noteId to it.caption }
                notes.map { note -> note.withCaption(captionMap) }
            }.withTranscripts(transcriptionRepository)

    fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> {
        // Get all notes-to-journal mappings for this journal
        return journalContentDao
            .getContentForJournal(journalId)
            .combine(allNotesObserved) { contentIds, allNotes ->
                allNotes.filter { note -> contentIds.contains(note.uid) }
            }
    }

    suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> =
        journalContentDao
            .getAllLinks()
            .map { link -> link.journalId to link.contentId }

    fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> {
        val startMillis = start.toEpochMilliseconds()
        val endMillis = end.toEpochMilliseconds()

        return notePlaceResolver
            .observeAll()
            .combine(
                observeNoteBuckets(
                    textFlow = textNoteDao.getNotesInRange(startMillis, endMillis),
                    imageFlow = imageNoteDao.getNotesInRange(startMillis, endMillis),
                    audioFlow = audioNoteDao.getNotesInRange(startMillis, endMillis),
                    videoFlow = videoNoteDao.getNotesInRange(startMillis, endMillis),
                ),
            ) { placeLookup, buckets ->
                buckets.toNotes(placeLookup)
            }.combine(mediaCaptionDao.observeAll()) { notes, captions ->
                val captionMap = captions.associate { it.noteId to it.caption }
                notes.map { note -> note.withCaption(captionMap) }
            }.withTranscripts(transcriptionRepository)
    }

    fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> {
        // Since AudioNoteDao and VideoNoteDao don't have getNotesPage, handle pagination in memory.
        return allNotesObserved.map { notes ->
            notes
                .sortedByDescending { it.creationTimestamp }
                .drop(offset)
                .take(pageSize)
        }
    }

    fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> =
        // Use the existing allNotesObserved for real-time updates
        // This gives us immediate responsiveness since Room already caches data
        allNotesObserved

    fun observeEntryTimestamps(): Flow<List<Instant>> =
        combine(
            textNoteDao.observeCreatedTimestamps(),
            imageNoteDao.observeCreatedTimestamps(),
            audioNoteDao.observeCreatedTimestamps(),
            videoNoteDao.observeCreatedTimestamps(),
        ) { text, image, audio, video ->
            (text + image + audio + video).map(Instant::fromEpochMilliseconds)
        }

    fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> =
        notePlaceResolver
            .observeAll()
            .combine(
                observeNoteBuckets(
                    textFlow = textNoteDao.getRecentNotes(limit),
                    imageFlow = imageNoteDao.getRecentNotes(limit),
                    audioFlow = audioNoteDao.getRecentNotes(limit),
                    videoFlow = videoNoteDao.getRecentNotes(limit),
                ),
            ) { placeLookup, buckets ->
                buckets
                    .toNotes(placeLookup)
                    .sortedByDescending { it.creationTimestamp }
                    .take(limit)
            }.combine(mediaCaptionDao.observeAll()) { notes, captions ->
                val captionMap = captions.associate { it.noteId to it.caption }
                notes.map { note -> note.withCaption(captionMap) }
            }.withTranscripts(transcriptionRepository)

    fun observeRecentAudioNotes(limit: Int): Flow<List<JournalNote.Audio>> =
        notePlaceResolver
            .observeAll()
            .combine(audioNoteDao.getRecentNotes(limit)) { placeLookup, entities ->
                entities.map { it.toModel(it.placeId?.let(placeLookup::get)) }
            }.combine(mediaCaptionDao.observeAll()) { notes, captions ->
                val captionMap = captions.associate { it.noteId to it.caption }
                notes.map { note -> note.copy(caption = captionMap[note.uid] ?: note.caption) }
            }.withTranscripts(transcriptionRepository)
            .map { it.filterIsInstance<JournalNote.Audio>() }

    fun observeNotesForDay(day: LocalDate): Flow<List<JournalNote>> {
        val timezone = TimeZone.currentSystemDefault()
        val start = day.atStartOfDayIn(timezone).toEpochMilliseconds()
        val endExclusive = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timezone).toEpochMilliseconds()

        return notePlaceResolver
            .observeAll()
            .combine(
                observeNoteBuckets(
                    textFlow = textNoteDao.getNotesInRange(start, endExclusive),
                    imageFlow = imageNoteDao.getNotesInRange(start, endExclusive),
                    audioFlow = audioNoteDao.getNotesInRange(start, endExclusive),
                    videoFlow = videoNoteDao.getNotesInRange(start, endExclusive),
                ),
            ) { placeLookup, buckets ->
                buckets
                    .toNotes(placeLookup)
                    .filter { note ->
                        note.creationTimestamp
                            .toLocalDateTime(timezone)
                            .date == day
                    }.sortedByDescending(JournalNote::creationTimestamp)
            }.combine(mediaCaptionDao.observeAll()) { notes, captions ->
                val captionMap = captions.associate { it.noteId to it.caption }
                notes.map { note -> note.withCaption(captionMap) }
            }.withTranscripts(transcriptionRepository)
    }

    suspend fun getNotesBefore(
        beforeExclusive: Instant,
        limit: Int,
    ): List<JournalNote> =
        NoteBuckets(
            textNotes = textNoteDao.getRecentNotesBefore(beforeExclusive.toEpochMilliseconds(), limit),
            imageNotes = imageNoteDao.getRecentNotesBefore(beforeExclusive.toEpochMilliseconds(), limit),
            audioNotes = audioNoteDao.getRecentNotesBefore(beforeExclusive.toEpochMilliseconds(), limit),
            videoNotes = videoNoteDao.getRecentNotesBefore(beforeExclusive.toEpochMilliseconds(), limit),
        ).toNotes(notePlaceResolver.observeAll().first())
            .sortedByDescending(JournalNote::creationTimestamp)
            .take(limit)
            .map { note ->
                note
                    .withCaption(mapOf(note.uid to (mediaCaptionDao.getCaption(note.uid)?.caption ?: "")))
                    .withTranscript(transcriptionRepository)
            }

    suspend fun hasNotesBefore(beforeExclusive: Instant): Boolean {
        val beforeTimestamp = beforeExclusive.toEpochMilliseconds()
        return textNoteDao.hasNotesBefore(beforeTimestamp) ||
            imageNoteDao.hasNotesBefore(beforeTimestamp) ||
            audioNoteDao.hasNotesBefore(beforeTimestamp) ||
            videoNoteDao.hasNotesBefore(beforeTimestamp)
    }

    suspend fun notesReferencingMediaPaths(paths: Set<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        val candidates = paths.toList()
        return buildSet {
            addAll(audioNoteDao.findReferencedContentUris(candidates))
            addAll(imageNoteDao.findReferencedContentUris(candidates))
            addAll(videoNoteDao.findReferencedContentUris(candidates))
        }
    }

    suspend fun getNoteById(noteId: Uuid): JournalNote? {
        // Try each note type DAO until we find the note
        runCatching {
            textNoteDao.getNoteOneOff(noteId).let { note ->
                note.toModel(note.placeId?.let { placeId -> notePlaceResolver.get(placeId) })
            }
        }.getOrNull()?.let { return it }
        runCatching {
            val caption = mediaCaptionDao.getCaption(noteId)?.caption ?: ""
            imageNoteDao.getNoteOneOff(noteId).let { note ->
                note.toModel(note.placeId?.let { placeId -> notePlaceResolver.get(placeId) }, caption)
            }
        }.getOrNull()?.let { return it }
        runCatching {
            val caption = mediaCaptionDao.getCaption(noteId)?.caption ?: ""
            audioNoteDao.getNoteOneOff(noteId).let { note ->
                note
                    .toModel(
                        note.placeId?.let { placeId ->
                            notePlaceResolver.get(placeId)
                        },
                        caption,
                    ).withTranscript(transcriptionRepository)
            }
        }.getOrNull()?.let { return it }
        runCatching {
            val caption = mediaCaptionDao.getCaption(noteId)?.caption ?: ""
            videoNoteDao.getNoteOneOff(noteId).let { note ->
                note.toModel(note.placeId?.let { placeId -> notePlaceResolver.get(placeId) }, caption)
            }
        }.getOrNull()?.let { return it }
        return null
    }

    private fun observeNoteBuckets(
        textFlow: Flow<List<TextNoteEntity>>,
        imageFlow: Flow<List<ImageNoteEntity>>,
        audioFlow: Flow<List<AudioNoteEntity>>,
        videoFlow: Flow<List<VideoNoteEntity>>,
    ): Flow<NoteBuckets> =
        textFlow
            .combine(imageFlow) { textNotes, imageNotes ->
                textNotes to imageNotes
            }.combine(audioFlow) { textAndImageNotes, audioNotes ->
                Triple(textAndImageNotes.first, textAndImageNotes.second, audioNotes)
            }.combine(videoFlow) { textImageAndAudioNotes, videoNotes ->
                NoteBuckets(
                    textNotes = textImageAndAudioNotes.first,
                    imageNotes = textImageAndAudioNotes.second,
                    audioNotes = textImageAndAudioNotes.third,
                    videoNotes = videoNotes,
                )
            }

    private data class NoteBuckets(
        val textNotes: List<TextNoteEntity>,
        val imageNotes: List<ImageNoteEntity>,
        val audioNotes: List<AudioNoteEntity>,
        val videoNotes: List<VideoNoteEntity>,
    ) {
        fun toNotes(placeLookup: Map<Uuid, NotePlace>): List<JournalNote> =
            textNotes.map { it.toModel(it.placeId?.let(placeLookup::get)) } +
                imageNotes.map { it.toModel(it.placeId?.let(placeLookup::get)) } +
                audioNotes.map { it.toModel(it.placeId?.let(placeLookup::get)) } +
                videoNotes.map { it.toModel(it.placeId?.let(placeLookup::get)) }
    }
}
