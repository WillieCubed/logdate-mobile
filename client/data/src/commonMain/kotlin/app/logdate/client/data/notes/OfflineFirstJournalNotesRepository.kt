package app.logdate.client.data.notes

import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.AudioNoteEntity
import app.logdate.client.database.entities.ImageNoteEntity
import app.logdate.client.database.entities.TextNoteEntity
import app.logdate.client.database.entities.VideoNoteEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.repository.journals.ExportableJournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.NotePlace
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.sync.NoOpSyncManager
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A repository for journal notes that stores data locally.
 */
class OfflineFirstJournalNotesRepository(
    private val textNoteDao: TextNoteDao,
    private val imageNoteDao: ImageNoteDao,
    private val audioNoteDao: AudioNoteDao,
    private val videoNoteDao: VideoNoteDao,
    private val journalContentDao: JournalContentDao,
    private val journalRepository: JournalRepository,
    private val notePlaceResolver: NotePlaceResolver = EmptyNotePlaceResolver,
    private val syncManagerProvider: () -> SyncManager = { NoOpSyncManager },
    private val syncMetadataService: SyncMetadataService,
    private val syncScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : JournalNotesRepository,
    ExportableJournalContentRepository,
    SyncableJournalNotesRepository {
    override val allNotesObserved: Flow<List<JournalNote>> =
        notePlaceResolver.observeAll().combine(
            observeNoteBuckets(
                textFlow = textNoteDao.getAllNotes(),
                imageFlow = imageNoteDao.getAllNotes(),
                audioFlow = audioNoteDao.getAllNotes(),
                videoFlow = videoNoteDao.getAllNotes(),
            ),
        ) { placeLookup, buckets ->
            buckets.toNotes(placeLookup)
        }

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> {
        // Get all notes-to-journal mappings for this journal
        return journalContentDao
            .getContentForJournal(journalId)
            .combine(allNotesObserved) { contentIds, allNotes ->
                allNotes.filter { note -> contentIds.contains(note.uid) }
            }
    }

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> {
        val startMillis = start.toEpochMilliseconds()
        val endMillis = end.toEpochMilliseconds()

        return notePlaceResolver.observeAll().combine(
            observeNoteBuckets(
                textFlow = textNoteDao.getNotesInRange(startMillis, endMillis),
                imageFlow = imageNoteDao.getNotesInRange(startMillis, endMillis),
                audioFlow = audioNoteDao.getNotesInRange(startMillis, endMillis),
                videoFlow = videoNoteDao.getNotesInRange(startMillis, endMillis),
            ),
        ) { placeLookup, buckets ->
            buckets.toNotes(placeLookup)
        }
    }

    override fun observeNotesPage(
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

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> =
        // Use the existing allNotesObserved for real-time updates
        // This gives us immediate responsiveness since Room already caches data
        allNotesObserved

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> =
        notePlaceResolver.observeAll().combine(
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
        }

    override fun observeNotesForDay(day: LocalDate): Flow<List<JournalNote>> {
        val timezone = TimeZone.currentSystemDefault()
        val start = day.atStartOfDayIn(timezone).toEpochMilliseconds()
        val endExclusive = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timezone).toEpochMilliseconds()

        return notePlaceResolver.observeAll().combine(
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
        }
    }

    override suspend fun getNotesBefore(
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

    override suspend fun hasNotesBefore(beforeExclusive: Instant): Boolean {
        val beforeTimestamp = beforeExclusive.toEpochMilliseconds()
        return textNoteDao.hasNotesBefore(beforeTimestamp) ||
            imageNoteDao.hasNotesBefore(beforeTimestamp) ||
            audioNoteDao.hasNotesBefore(beforeTimestamp) ||
            videoNoteDao.hasNotesBefore(beforeTimestamp)
    }

    override suspend fun getNoteById(noteId: Uuid): JournalNote? {
        // Try each note type DAO until we find the note
        runCatching {
            textNoteDao.getNoteOneOff(noteId).let { note ->
                note.toModel(note.placeId?.let { placeId -> notePlaceResolver.get(placeId) })
            }
        }.getOrNull()?.let { return it }
        runCatching {
            imageNoteDao.getNoteOneOff(noteId).let { note ->
                note.toModel(note.placeId?.let { placeId -> notePlaceResolver.get(placeId) })
            }
        }.getOrNull()?.let { return it }
        runCatching {
            audioNoteDao.getNoteOneOff(noteId).let { note ->
                note.toModel(note.placeId?.let { placeId -> notePlaceResolver.get(placeId) })
            }
        }.getOrNull()?.let { return it }
        runCatching {
            videoNoteDao.getNoteOneOff(noteId).let { note ->
                note.toModel(note.placeId?.let { placeId -> notePlaceResolver.get(placeId) })
            }
        }.getOrNull()?.let { return it }
        return null
    }

    override suspend fun create(note: JournalNote): Uuid {
        val noteId =
            when (note) {
                is JournalNote.Text -> {
                    textNoteDao.addNote(note.toEntity())
                    note.uid
                }

                is JournalNote.Image -> {
                    imageNoteDao.addNote(note.toEntity())
                    note.uid
                }

                is JournalNote.Audio -> {
                    audioNoteDao.addNote(note.toEntity())
                    note.uid
                }

                is JournalNote.Video -> {
                    videoNoteDao.addNote(note.toEntity())
                    note.uid
                }
            }

        syncMetadataService.enqueuePending(
            entityId = note.uid.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.CREATE,
        )

        triggerContentSync()

        return noteId
    }

    override suspend fun remove(note: JournalNote) {
        val journalIds = journalContentDao.getJournalsForContent(note.uid).first()

        when (note) {
            is JournalNote.Text -> {
                textNoteDao.removeNote(note.uid)
            }

            is JournalNote.Image -> {
                imageNoteDao.removeNote(note.uid)
            }

            is JournalNote.Audio -> {
                audioNoteDao.removeNote(note.uid)
            }

            is JournalNote.Video -> {
                videoNoteDao.removeNote(note.uid)
            }
        }

        journalIds.forEach { journalId ->
            syncMetadataService.enqueuePending(
                entityId = AssociationPendingKey(journalId, note.uid).toPendingId(),
                entityType = EntityType.ASSOCIATION,
                operation = PendingOperation.DELETE,
            )
        }

        journalContentDao.removeContentFromAllJournals(note.uid)

        syncMetadataService.enqueuePending(
            entityId = note.uid.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.DELETE,
        )

        triggerContentSync()
        if (journalIds.isNotEmpty()) {
            triggerAssociationSync()
        }
    }

    override suspend fun removeById(noteId: Uuid) {
        val journalIds = journalContentDao.getJournalsForContent(noteId).first()

        textNoteDao.removeNote(noteId)
        imageNoteDao.removeNote(noteId)
        audioNoteDao.removeNote(noteId)
        videoNoteDao.removeNote(noteId)

        journalIds.forEach { journalId ->
            syncMetadataService.enqueuePending(
                entityId = AssociationPendingKey(journalId, noteId).toPendingId(),
                entityType = EntityType.ASSOCIATION,
                operation = PendingOperation.DELETE,
            )
        }

        journalContentDao.removeContentFromAllJournals(noteId)

        syncMetadataService.enqueuePending(
            entityId = noteId.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.DELETE,
        )

        triggerContentSync()
        if (journalIds.isNotEmpty()) {
            triggerAssociationSync()
        }
    }

    override suspend fun create(
        note: JournalNote,
        journalId: Uuid,
    ) {
        // Add the note first
        create(note)

        // Link it to the journal
        journalContentDao.addContentToJournal(JournalContentEntityLink(journalId, note.uid))

        syncMetadataService.enqueuePending(
            entityId = AssociationPendingKey(journalId, note.uid).toPendingId(),
            entityType = EntityType.ASSOCIATION,
            operation = PendingOperation.CREATE,
        )

        triggerAssociationSync()
    }

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {
        journalContentDao.removeContentFromJournal(journalId, noteId)

        syncMetadataService.enqueuePending(
            entityId = AssociationPendingKey(journalId, noteId).toPendingId(),
            entityType = EntityType.ASSOCIATION,
            operation = PendingOperation.DELETE,
        )

        triggerAssociationSync()
    }

    override suspend fun createFromSync(note: JournalNote) {
        when (note) {
            is JournalNote.Text -> textNoteDao.addNote(note.toEntity())
            is JournalNote.Image -> imageNoteDao.addNote(note.toEntity())
            is JournalNote.Audio -> audioNoteDao.addNote(note.toEntity())
            is JournalNote.Video -> videoNoteDao.addNote(note.toEntity())
        }
    }

    override suspend fun deleteFromSync(noteId: Uuid) {
        textNoteDao.removeNote(noteId)
        imageNoteDao.removeNote(noteId)
        audioNoteDao.removeNote(noteId)
        videoNoteDao.removeNote(noteId)
    }

    override suspend fun updateSyncMetadata(
        note: JournalNote,
        syncVersion: Long,
        syncedAt: Instant,
    ) {
        when (note) {
            is JournalNote.Text -> textNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Image -> imageNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Audio -> audioNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
            is JournalNote.Video -> videoNoteDao.updateSyncMetadata(note.uid, syncVersion, syncedAt)
        }
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

    override suspend fun updateMediaRef(
        noteId: Uuid,
        mediaRef: String,
    ) {
        imageNoteDao.updateContentUri(noteId, mediaRef)
        audioNoteDao.updateContentUri(noteId, mediaRef)
        videoNoteDao.updateContentUri(noteId, mediaRef)
    }

    private fun triggerContentSync() {
        syncScope.launch {
            try {
                syncManagerProvider().syncContent()
            } catch (e: Exception) {
                Napier.w("Failed to sync content after note change", e)
            }
        }
    }

    private fun triggerAssociationSync() {
        syncScope.launch {
            try {
                syncManagerProvider().syncAssociations()
            } catch (e: Exception) {
                Napier.w("Failed to sync associations after journal change", e)
            }
        }
    }

    override suspend fun exportContentToFile(
        destination: String,
        overwrite: Boolean,
        startTimestamp: Instant,
        endTimestamp: Instant,
    ) {
        // Get all notes within the time range
        val textNotes =
            textNoteDao
                .getNotesInRange(
                    startTimestamp.toEpochMilliseconds(),
                    endTimestamp.toEpochMilliseconds(),
                ).first()
                .map { it.toModel() }

        val imageNotes =
            imageNoteDao
                .getNotesInRange(
                    startTimestamp.toEpochMilliseconds(),
                    endTimestamp.toEpochMilliseconds(),
                ).first()
                .map { it.toModel() }

        val audioNotes =
            audioNoteDao
                .getNotesInRange(
                    startTimestamp.toEpochMilliseconds(),
                    endTimestamp.toEpochMilliseconds(),
                ).first()
                .map { it.toModel() }

        val videoNotes =
            videoNoteDao
                .getNotesInRange(
                    startTimestamp.toEpochMilliseconds(),
                    endTimestamp.toEpochMilliseconds(),
                ).first()
                .map { it.toModel() }

        // Combine all notes
        val allNotes = textNotes + imageNotes + audioNotes + videoNotes

        // Get all journals
        val journals = journalRepository.allJournalsObserved.first()

        // Create a map of journal ID to note IDs
        val journalToNotesMap = mutableMapOf<Uuid, List<Uuid>>()

        // For each journal, get all its notes and add them to the map
        journals.forEach { journal ->
            // Get notes for this journal
            val journalNotes = observeNotesInJournal(journal.id).first()

            // Map to just the IDs
            val noteIds = journalNotes.map { it.uid }

            // Only add to map if there are notes
            if (noteIds.isNotEmpty()) {
                journalToNotesMap[journal.id] = noteIds
            }
        }

        // Create export structure
        val backup =
            JournalContentBackup(
                notes = allNotes,
                journalToNotesMap = journalToNotesMap,
                generated = Clock.System.now(),
            )

        // Serialize to JSON
        val json =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }
        val jsonContent = json.encodeToString(backup)

        writeExportFile(destination, jsonContent, overwrite)
    }
}

@Serializable
data class JournalContentBackup(
    val notes: List<JournalNote>,
    @Serializable(with = UuidToUuidListMapSerializer::class)
    val journalToNotesMap: Map<Uuid, List<Uuid>> = emptyMap(),
    val generated: Instant = Clock.System.now(),
    val version: String = "1.0",
)
