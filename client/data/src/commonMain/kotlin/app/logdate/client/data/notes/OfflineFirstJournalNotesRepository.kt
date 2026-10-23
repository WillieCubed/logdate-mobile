package app.logdate.client.data.notes

import app.logdate.client.data.journals.ArchiveRecoveryReceipts
import app.logdate.client.database.dao.AudioNoteDao
import app.logdate.client.database.dao.ImageNoteDao
import app.logdate.client.database.dao.MediaCaptionDao
import app.logdate.client.database.dao.TextNoteDao
import app.logdate.client.database.dao.VideoNoteDao
import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.MediaCaptionEntity
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.media.MediaManager
import app.logdate.client.media.MediaObject
import app.logdate.client.media.storage.StoredMediaReferences
import app.logdate.client.repository.journals.ExportableJournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalNotesRepository
import app.logdate.client.repository.media.IndexedMediaRepository
import app.logdate.client.repository.transcription.TranscriptionRepository
import app.logdate.client.sync.NoOpSyncManager
import app.logdate.client.sync.SyncDebouncer
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
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
    private val mediaCaptionDao: MediaCaptionDao,
    private val notePlaceResolver: NotePlaceResolver = EmptyNotePlaceResolver,
    private val indexedMediaRepository: IndexedMediaRepository? = null,
    private val mediaManager: MediaManager? = null,
    private val transactionManager: SyncTransactionManager = PassthroughSyncTransactionManager,
    private val syncManagerProvider: () -> SyncManager = { NoOpSyncManager },
    private val syncMetadataService: SyncMetadataService,
    private val syncScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val transcriptionRepository: TranscriptionRepository? = null,
    private val mediaReferences: StoredMediaReferences = StoredMediaReferences.Unchanged,
    private val recoveryReceipts: ArchiveRecoveryReceipts = ArchiveRecoveryReceipts.None,
    private val currentRecoveryScope: suspend () -> app.logdate.client.repository.journals.JournalMergeScope? = { null },
) : JournalNotesRepository,
    ExportableJournalContentRepository,
    SyncableJournalNotesRepository {
    init {
        transcriptionRepository?.startAutomaticTranscriptions(syncScope)
    }

    private val queries =
        JournalNoteQueries(
            textNoteDao,
            imageNoteDao,
            audioNoteDao,
            videoNoteDao,
            journalContentDao,
            mediaCaptionDao,
            notePlaceResolver,
            transcriptionRepository,
        )

    override val allNotesObserved: Flow<List<JournalNote>> = queries.allNotesObserved

    override fun observeNotesInJournal(journalId: Uuid): Flow<List<JournalNote>> = queries.observeNotesInJournal(journalId)

    override suspend fun getAllJournalNoteLinks(): List<Pair<Uuid, Uuid>> = queries.getAllJournalNoteLinks()

    override fun observeNotesInRange(
        start: Instant,
        end: Instant,
    ): Flow<List<JournalNote>> = queries.observeNotesInRange(start, end)

    override fun observeNotesPage(
        pageSize: Int,
        offset: Int,
    ): Flow<List<JournalNote>> = queries.observeNotesPage(pageSize, offset)

    override fun observeNotesStream(pageSize: Int): Flow<List<JournalNote>> = queries.observeNotesStream(pageSize)

    override fun observeEntryTimestamps(): Flow<List<Instant>> = queries.observeEntryTimestamps()

    override fun observeRecentNotes(limit: Int): Flow<List<JournalNote>> = queries.observeRecentNotes(limit)

    override fun observeRecentAudioNotes(limit: Int): Flow<List<JournalNote.Audio>> = queries.observeRecentAudioNotes(limit)

    override fun observeNotesForDay(day: LocalDate): Flow<List<JournalNote>> = queries.observeNotesForDay(day)

    override suspend fun getNotesBefore(
        beforeExclusive: Instant,
        limit: Int,
    ): List<JournalNote> = queries.getNotesBefore(beforeExclusive, limit)

    override suspend fun hasNotesBefore(beforeExclusive: Instant): Boolean = queries.hasNotesBefore(beforeExclusive)

    /**
     * Returns the [paths] a note still references, recognising a file under any spelling: a
     * note may store a `logdate-media://` reference for a file a draft names by its path.
     */
    override suspend fun notesReferencingMediaPaths(paths: Set<String>): Set<String> {
        if (paths.isEmpty()) return emptySet()
        val spellings = paths.associateWith(mediaReferences::spellingsOf)
        val referenced = queries.notesReferencingMediaPaths(spellings.values.flatten().toSet())
        return paths.filterTo(mutableSetOf()) { path -> spellings.getValue(path).any(referenced::contains) }
    }

    override suspend fun getNoteById(noteId: Uuid): JournalNote? = queries.getNoteById(noteId)

    override suspend fun create(note: JournalNote): Uuid {
        val storedNote = note.withStoredMediaRef(mediaReferences)
        val pendingMediaIndex = buildPendingMediaIndex(storedNote)
        val noteId =
            transactionManager.withTransaction {
                // Keep note persistence, optional index creation, and sync metadata aligned so a
                // local write failure cannot leave orphaned indexed media behind.
                createNoteRecord(
                    note = storedNote,
                    pendingMediaIndex = pendingMediaIndex,
                )
            }

        triggerContentSync()

        return noteId
    }

    private suspend fun createNoteRecord(
        note: JournalNote,
        pendingMediaIndex: PendingMediaIndex?,
    ): Uuid {
        getNoteById(note.uid)?.let { existing ->
            check(existing.withStoredMediaRef(mediaReferences).hasSamePersistedContent(note)) {
                "This note already exists with different content. Keep your changes in a draft or save them as a new entry."
            }
            return existing.uid
        }
        val linkedPlaceId =
            note.location
                ?.place
                ?.id
                ?.let { notePlaceResolver.get(it)?.id }
        val noteId =
            when (note) {
                is JournalNote.Text -> {
                    textNoteDao.addNote(note.toEntity(linkedPlaceId))
                    note.uid
                }

                is JournalNote.Image -> {
                    imageNoteDao.addNote(note.toEntity(linkedPlaceId))
                    if (note.caption.isNotEmpty()) {
                        mediaCaptionDao.upsertCaption(MediaCaptionEntity(note.uid, note.caption))
                    }
                    note.uid
                }

                is JournalNote.Audio -> {
                    audioNoteDao.addNote(note.toEntity(linkedPlaceId))
                    mediaCaptionDao.upsertCaption(MediaCaptionEntity(note.uid, note.caption))
                    note.persistTranscript(transcriptionRepository)
                    note.uid
                }

                is JournalNote.Video -> {
                    videoNoteDao.addNote(note.withDurationFrom(pendingMediaIndex).toEntity(linkedPlaceId))
                    if (note.caption.isNotEmpty()) {
                        mediaCaptionDao.upsertCaption(MediaCaptionEntity(note.uid, note.caption))
                    }
                    note.uid
                }
            }

        indexPendingMediaIfNeeded(pendingMediaIndex)
        syncMetadataService.enqueuePending(
            entityId = note.uid.toString(),
            entityType = EntityType.NOTE,
            operation = PendingOperation.CREATE,
        )

        return noteId
    }

    private fun JournalNote.Video.withDurationFrom(pendingMediaIndex: PendingMediaIndex?): JournalNote.Video {
        if (durationMs != null) return this
        val duration = (pendingMediaIndex as? PendingMediaIndex.Video)?.duration ?: return this
        return copy(durationMs = duration.inWholeMilliseconds)
    }

    private suspend fun buildPendingMediaIndex(note: JournalNote): PendingMediaIndex? {
        val manager = mediaManager ?: return null
        indexedMediaRepository ?: return null

        return when (note) {
            is JournalNote.Image -> {
                manager
                    .getMedia(note.mediaRef)
                    .also { check(it is MediaObject.Image) { "Expected image media for ${note.mediaRef}" } }
                PendingMediaIndex.Image(
                    uri = note.mediaRef,
                    timestamp = note.creationTimestamp,
                )
            }

            is JournalNote.Video -> {
                val media =
                    manager
                        .getMedia(note.mediaRef)
                        .also { check(it is MediaObject.Video) { "Expected video media for ${note.mediaRef}" } } as MediaObject.Video
                PendingMediaIndex.Video(
                    uri = note.mediaRef,
                    timestamp = note.creationTimestamp,
                    duration = media.duration,
                )
            }

            else -> null
        }
    }

    private suspend fun indexPendingMediaIfNeeded(pendingMediaIndex: PendingMediaIndex?) {
        val repository = indexedMediaRepository ?: return
        val pending = pendingMediaIndex ?: return
        if (repository.isIndexed(pending.uri)) return

        when (pending) {
            is PendingMediaIndex.Image ->
                repository.indexImage(
                    uri = pending.uri,
                    timestamp = pending.timestamp,
                )
            is PendingMediaIndex.Video ->
                repository.indexVideo(
                    uri = pending.uri,
                    timestamp = pending.timestamp,
                    duration = pending.duration,
                )
        }
    }

    /**
     * Deletes owned media only after the last note reference, including legacy path spellings,
     * is gone. [MediaManager.deleteOwnedMedia] preserves files from the user's own library.
     */
    private suspend fun deleteMediaIfUnreferenced(contentUri: String?) {
        if (contentUri.isNullOrEmpty()) return
        val manager = mediaManager ?: return

        val remainingReferences =
            mediaReferences.spellingsOf(contentUri).sumOf { spelling ->
                imageNoteDao.countByContentUri(spelling) +
                    videoNoteDao.countByContentUri(spelling) +
                    audioNoteDao.countByContentUri(spelling)
            }
        if (remainingReferences > 0) return

        runCatching { manager.deleteOwnedMedia(contentUri) }
            .onFailure { Napier.w("Could not remove media for a deleted note", it) }
    }

    override suspend fun remove(note: JournalNote) {
        val journalIds = journalContentDao.getJournalsForContent(note.uid).first()

        when (note) {
            is JournalNote.Text -> {
                textNoteDao.removeNote(note.uid)
            }

            is JournalNote.Image -> {
                imageNoteDao.removeNote(note.uid)
                mediaCaptionDao.deleteCaption(note.uid)
                deleteMediaIfUnreferenced(note.mediaRef)
            }

            is JournalNote.Audio -> {
                transcriptionRepository?.deleteTranscription(note.uid)
                audioNoteDao.removeNote(note.uid)
                mediaCaptionDao.deleteCaption(note.uid)
                deleteMediaIfUnreferenced(note.mediaRef)
            }

            is JournalNote.Video -> {
                videoNoteDao.removeNote(note.uid)
                mediaCaptionDao.deleteCaption(note.uid)
                deleteMediaIfUnreferenced(note.mediaRef)
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
        // Read before deleting: once the rows are gone there is no way back to the media.
        val mediaRef = runCatching { getNoteById(noteId) }.getOrNull()?.mediaRefOrNull()

        textNoteDao.removeNote(noteId)
        imageNoteDao.removeNote(noteId)
        transcriptionRepository?.deleteTranscription(noteId)
        audioNoteDao.removeNote(noteId)
        videoNoteDao.removeNote(noteId)
        mediaCaptionDao.deleteCaption(noteId)

        deleteMediaIfUnreferenced(mediaRef)

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
        val storedNote = note.withStoredMediaRef(mediaReferences)
        val pendingMediaIndex = buildPendingMediaIndex(storedNote)
        transactionManager.withTransaction {
            createNoteRecord(
                note = storedNote,
                pendingMediaIndex = pendingMediaIndex,
            )
            val destination = journalRepository.resolveJournalId(journalId)
            journalContentDao.addContentToJournal(JournalContentEntityLink(destination, note.uid))
            syncMetadataService.enqueuePending(
                entityId = AssociationPendingKey(destination, note.uid).toPendingId(),
                entityType = EntityType.ASSOCIATION,
                operation = PendingOperation.CREATE,
            )
        }

        triggerContentSync()
        triggerAssociationSync()
    }

    override suspend fun removeFromJournal(
        noteId: Uuid,
        journalId: Uuid,
    ) {
        if (journalRepository.resolveJournalId(journalId) != journalId) return
        journalContentDao.removeContentFromJournal(journalId, noteId)

        syncMetadataService.enqueuePending(
            entityId = AssociationPendingKey(journalId, noteId).toPendingId(),
            entityType = EntityType.ASSOCIATION,
            operation = PendingOperation.DELETE,
        )

        triggerAssociationSync()
    }

    private val recovery by lazy {
        JournalNoteRecoveryPersistence(
            textNoteDao,
            imageNoteDao,
            audioNoteDao,
            videoNoteDao,
            mediaCaptionDao,
            transcriptionRepository,
            transactionManager,
            syncMetadataService,
            mediaReferences,
            ::getNoteById,
            currentRecoveryScope,
            ::triggerContentSync,
            ::indexAvailableRecoveryMedia,
            recoveryReceipts,
            notePlaceResolver,
        )
    }

    private suspend fun indexAvailableRecoveryMedia(note: JournalNote) {
        val reference = note.mediaRefOrNull() ?: return
        if (mediaManager?.exists(reference) == true) indexPendingMediaIfNeeded(buildPendingMediaIndex(note))
    }

    override suspend fun getNoteRecoveryScope() = currentRecoveryScope()

    override suspend fun compareAndSetNoteForRestore(
        expected: JournalNote?,
        replacement: JournalNote,
        scope: app.logdate.client.repository.journals.JournalMergeScope?,
        recoveryId: String?,
    ): Boolean = recovery.compareAndSet(expected, replacement, scope, recoveryId)

    override suspend fun createFromSync(note: JournalNote) = recovery.createFromSync(note)

    override suspend fun deleteFromSync(noteId: Uuid) = recovery.deleteFromSync(noteId)

    override suspend fun updateSyncMetadata(
        note: JournalNote,
        syncVersion: Long,
        syncedAt: Instant,
    ) = recovery.updateSyncMetadata(note, syncVersion, syncedAt)

    private val mediaMetadata =
        NoteMediaMetadataWriter(
            audioNoteDao,
            imageNoteDao,
            videoNoteDao,
            transactionManager,
            syncMetadataService,
            mediaReferences,
            transcriptionRepository,
            ::getNoteById,
        )

    override suspend fun updateAudioDurationIfUnknown(
        noteId: Uuid,
        mediaRef: String,
        durationMs: Long,
    ): Boolean = mediaMetadata.fillAudioDuration(noteId, mediaRef, durationMs).also { changed -> if (changed) triggerContentSync() }

    override suspend fun updateMediaRef(
        noteId: Uuid,
        mediaRef: String,
    ) = mediaMetadata.updateReference(noteId, mediaRef)

    private val contentSyncDebouncer =
        SyncDebouncer(scope = syncScope) {
            syncManagerProvider().syncContent()
        }

    private val associationSyncDebouncer =
        SyncDebouncer(scope = syncScope) {
            syncManagerProvider().syncAssociations()
        }

    private fun triggerContentSync() = contentSyncDebouncer.trigger()

    private fun triggerAssociationSync() = associationSyncDebouncer.trigger()

    override suspend fun exportContentToFile(
        destination: String,
        overwrite: Boolean,
        startTimestamp: Instant,
        endTimestamp: Instant,
    ) = JournalNoteExporter(
        textNoteDao,
        imageNoteDao,
        audioNoteDao,
        videoNoteDao,
        mediaCaptionDao,
        journalRepository,
        transcriptionRepository,
        queries,
    ).exportContentToFile(destination, overwrite, startTimestamp, endTimestamp)
}
