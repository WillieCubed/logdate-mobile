package app.logdate.client.data.journals

import app.logdate.client.database.dao.JournalDao
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.sync.SyncManager
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val syncManager: SyncManager? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val externalScope: CoroutineScope = CoroutineScope(dispatcher),
) : JournalRepository {

    override val allJournalsObserved: Flow<List<Journal>>
        get() = journalDao.observeAll().map { journals ->
            journals.map { it.toModel() }
        }

    override fun observeJournalById(id: Uuid): Flow<Journal> {
        return journalDao.observeJournalById(id).map { it.toModel() }
    }
    
    override suspend fun getJournalById(id: Uuid): Journal? = withContext(dispatcher) {
        journalDao.getJournalById(id)?.toModel()
    }

    override suspend fun create(journal: Journal): Uuid = withContext(dispatcher) {
        val journalId = externalScope.async {
            journalDao.create(journal.toEntity())
            journal.id // Return the UUID from the journal
        }.await()
        
        // Trigger sync after successful creation
        syncManager?.let { syncManager ->
            externalScope.launch {
                try {
                    syncManager.syncJournals()
                } catch (e: Exception) {
                    Napier.w("Failed to sync after journal creation", e)
                }
            }
        }
        
        journalId
    }
    
    override suspend fun update(journal: Journal): Unit = withContext(dispatcher) {
        journalDao.update(journal.toEntity())
        
        // Trigger sync after successful update
        syncManager?.let { syncManager ->
            externalScope.launch {
                try {
                    syncManager.syncJournals()
                } catch (e: Exception) {
                    Napier.w("Failed to sync after journal update", e)
                }
            }
        }
    }

    override suspend fun delete(journalId: Uuid): Unit = withContext(dispatcher) {
        externalScope.async {
            journalDao.delete(journalId)
        }.await()
        
        // Trigger sync after successful deletion
        syncManager?.let { syncManager ->
            externalScope.launch {
                try {
                    syncManager.syncJournals()
                } catch (e: Exception) {
                    Napier.w("Failed to sync after journal deletion", e)
                }
            }
        }
    }
    
    // Draft-related methods delegating to the DraftRepository
    
    override suspend fun saveDraft(draft: EditorDraft) = withContext(dispatcher) {
        draftRepository.saveDraft(draft)
    }
    
    override suspend fun getLatestDraft(): EditorDraft? = withContext(dispatcher) {
        draftRepository.getLatestDraft()
    }
    
    override suspend fun getAllDrafts(): List<EditorDraft> = withContext(dispatcher) {
        draftRepository.getAllDrafts()
    }
    
    override suspend fun getDraft(id: Uuid): EditorDraft? = withContext(dispatcher) {
        draftRepository.getDraft(id)
    }
    
    override suspend fun deleteDraft(id: Uuid) = withContext(dispatcher) {
        draftRepository.deleteDraft(id)
    }

    private fun syncLocalWithRemote() {
        externalScope.launch(dispatcher) {
            val localJournals = journalDao.getAll()
            val remoteJournals = remoteDataSource.observeAllJournals()
        }
    }
}

