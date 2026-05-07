package app.logdate.client.data.journals

import app.logdate.client.database.dao.JournalDao
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.NoOpSyncManager
import app.logdate.client.sync.SyncDebouncer
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.util.platformIODispatcher
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A [JournalRepository] that uses a local database as the single source of truth.
 *
 * Note that this repository is an interface for all journals stored on device, not necessarily
 * journals that belong to any given user. To access user-specific data for journals, use
 * a [JournalUserDataRepository].
 */
class OfflineFirstJournalRepository(
    private val journalDao: JournalDao,
    private val remoteDataSource: RemoteJournalDataSource,
    private val draftRepository: DraftRepository,
    private val syncManagerProvider: () -> SyncManager = { NoOpSyncManager },
    private val syncMetadataService: SyncMetadataService,
    private val dispatcher: CoroutineDispatcher = platformIODispatcher,
    private val externalScope: CoroutineScope = CoroutineScope(dispatcher),
) : JournalRepository,
    SyncableJournalRepository {
    private val syncDebouncer =
        SyncDebouncer(scope = externalScope) {
            syncManagerProvider().syncJournals()
        }

    override val allJournalsObserved: Flow<List<Journal>>
        get() =
            journalDao.observeAll().map { journals ->
                journals.map { it.toModel() }
            }

    override fun observeJournalById(id: Uuid): Flow<Journal> = journalDao.observeJournalById(id).map { it.toModel() }

    override suspend fun getJournalById(id: Uuid): Journal? =
        withContext(dispatcher) {
            journalDao.getJournalById(id)?.toModel()
        }

    override suspend fun create(journal: Journal): Uuid =
        withContext(dispatcher) {
            val journalId =
                externalScope
                    .async {
                        journalDao.create(journal.toEntity())
                        journal.id // Return the UUID from the journal
                    }.await()

            syncMetadataService.enqueuePending(
                entityId = journal.id.toString(),
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.CREATE,
            )

            syncDebouncer.trigger()

            journalId
        }

    override suspend fun update(journal: Journal): Unit =
        withContext(dispatcher) {
            journalDao.update(journal.toEntity())

            syncMetadataService.enqueuePending(
                entityId = journal.id.toString(),
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.UPDATE,
            )

            syncDebouncer.trigger()
        }

    override suspend fun delete(journalId: Uuid): Unit =
        withContext(dispatcher) {
            externalScope
                .async {
                    journalDao.delete(journalId)
                }.await()

            syncMetadataService.enqueuePending(
                entityId = journalId.toString(),
                entityType = EntityType.JOURNAL,
                operation = PendingOperation.DELETE,
            )

            syncDebouncer.trigger()
        }

    // Draft-related methods delegating to the DraftRepository

    override suspend fun saveDraft(draft: EditorDraft) =
        withContext(dispatcher) {
            draftRepository.saveDraft(draft)
        }

    override suspend fun getLatestDraft(): EditorDraft? =
        withContext(dispatcher) {
            draftRepository.getLatestDraft()
        }

    override suspend fun getAllDrafts(): List<EditorDraft> =
        withContext(dispatcher) {
            draftRepository.getAllDrafts()
        }

    override suspend fun getDraft(id: Uuid): EditorDraft? =
        withContext(dispatcher) {
            draftRepository.getDraft(id)
        }

    override suspend fun deleteDraft(id: Uuid) =
        withContext(dispatcher) {
            draftRepository.deleteDraft(id)
        }

    override suspend fun createFromSync(journal: Journal) =
        withContext(dispatcher) {
            // Use upsert to be idempotent — the Data Layer may deliver duplicates
            journalDao.update(journal.toEntity())
        }

    override suspend fun updateFromSync(journal: Journal) =
        withContext(dispatcher) {
            journalDao.update(journal.toEntity())
        }

    override suspend fun deleteFromSync(journalId: Uuid) =
        withContext(dispatcher) {
            journalDao.delete(journalId)
        }

    override suspend fun updateSyncMetadata(
        journalId: Uuid,
        syncVersion: Long,
        syncedAt: Instant,
    ) = withContext(dispatcher) {
        journalDao.updateSyncMetadata(journalId, syncVersion, syncedAt)
    }

    private fun syncLocalWithRemote() {
        externalScope.launch(dispatcher) {
            val localJournals = journalDao.getAll()
            val remoteJournals = remoteDataSource.observeAllJournals()
        }
    }
}
