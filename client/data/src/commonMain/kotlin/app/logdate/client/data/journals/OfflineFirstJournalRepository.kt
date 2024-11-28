package app.logdate.client.data.journals

import app.logdate.client.database.dao.JournalDao
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val externalScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JournalRepository {

    override val allJournalsObserved: Flow<List<Journal>>
        get() = journalDao.observeAll().map { journals ->
            journals.map { it.toModel() }
        }

    override fun observeJournalById(id: String): Flow<Journal> {
        return journalDao.observeJournalById(id).map { it.toModel() }
    }

    override suspend fun create(journal: Journal): String = withContext(dispatcher) {
        externalScope.async {
            journalDao.create(journal.toEntity()).toString()
        }.await()
    }

    override suspend fun delete(journalId: String) = withContext(dispatcher) {
        externalScope.async {
            journalDao.delete(journalId)
        }.await()
    }

    private fun syncLocalWithRemote() {
        externalScope.launch(dispatcher) {
            val localJournals = journalDao.getAll()
            val remoteJournals = remoteDataSource.observeAllJournals()
        }
    }
}

