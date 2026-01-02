package app.logdate.client.data.fakes

import app.logdate.client.database.dao.JournalDao
import app.logdate.client.database.entities.JournalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Fake implementation of [JournalDao] for testing.
 */
class FakeJournalDao : JournalDao {
    private val journals = mutableMapOf<Uuid, JournalEntity>()
    private val journalsFlow = MutableStateFlow<List<JournalEntity>>(emptyList())
    
    override fun observeJournalById(id: Uuid): Flow<JournalEntity> {
        return journalsFlow.map { journals ->
            journals.find { it.id == id } ?: throw NoSuchElementException("Journal with ID $id not found")
        }
    }
    
    override suspend fun getJournalById(id: Uuid): JournalEntity? {
        return journals[id]
    }

    override fun observeAll(): Flow<List<JournalEntity>> {
        return journalsFlow
    }

    override suspend fun getAll(): List<JournalEntity> {
        return journals.values.toList()
    }

    override suspend fun create(journal: JournalEntity) {
        journals[journal.id] = journal
        updateFlow()
    }

    override suspend fun update(journal: JournalEntity) {
        journals[journal.id] = journal
        updateFlow()
    }

    override suspend fun updateSyncMetadata(journalId: Uuid, syncVersion: Long, lastSynced: kotlinx.datetime.Instant) {
        val existing = journals[journalId] ?: return
        journals[journalId] = existing.copy(syncVersion = syncVersion, lastSynced = lastSynced)
        updateFlow()
    }

    override suspend fun delete(journalId: Uuid) {
        journals.remove(journalId)
        updateFlow()
    }
    
    /**
     * Clears all journals in the fake database.
     * This method is specific to the fake implementation for testing.
     */
    fun clear() {
        journals.clear()
        updateFlow()
    }
    
    private fun updateFlow() {
        journalsFlow.value = journals.values.toList()
    }
}
