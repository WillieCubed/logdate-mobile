package app.logdate.core.data.journals

import app.logdate.core.coroutines.AppDispatcher.IO
import app.logdate.core.coroutines.Dispatcher
import app.logdate.core.data.JournalRepository
import app.logdate.core.data.journals.util.toEntity
import app.logdate.core.data.journals.util.toModel
import app.logdate.core.database.dao.JournalDao
import app.logdate.core.di.ApplicationScope
import app.logdate.model.Journal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A [JournalRepository] that uses a local database as the single source of truth.
 *
 * Note that this repository is an interface for all journals stored on device, not necessarily
 * journals that belong to any given user. To access user-specific data for journals, use
 * a [JournalUserDataRepository].
 */
class OfflineFirstJournalRepository @Inject constructor(
    private val journalDao: JournalDao,
    @ApplicationScope private val externalScope: CoroutineScope,
    @Dispatcher(IO) private val dispatcher: CoroutineDispatcher,
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
}