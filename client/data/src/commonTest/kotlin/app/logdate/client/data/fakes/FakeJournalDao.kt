package app.logdate.client.data.fakes

import app.logdate.client.database.dao.JournalDao
import app.logdate.client.database.entities.JournalCoverUri
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

    override fun observeJournalById(id: Uuid): Flow<JournalEntity> =
        journalsFlow.map { journals ->
            journals.find { it.id == id } ?: throw NoSuchElementException("Journal with ID $id not found")
        }

    override suspend fun getJournalById(id: Uuid): JournalEntity? = journals[id]

    override fun observeAll(): Flow<List<JournalEntity>> = journalsFlow

    override suspend fun getAll(): List<JournalEntity> = journals.values.toList()

    override suspend fun create(journal: JournalEntity) {
        journals[journal.id] = journal
        updateFlow()
    }

    override suspend fun update(journal: JournalEntity) {
        journals[journal.id] = journal
        updateFlow()
    }

    override suspend fun updateSyncMetadata(
        journalId: Uuid,
        syncVersion: Long,
        lastSynced: kotlin.time.Instant,
    ) {
        val existing = journals[journalId] ?: return
        journals[journalId] = existing.copy(syncVersion = syncVersion, lastSynced = lastSynced)
        updateFlow()
    }

    override suspend fun localFileCovers(): List<JournalCoverUri> =
        journals.values
            .mapNotNull { journal -> journal.coverImageUri?.let { JournalCoverUri(journal.id, it) } }
            .filter { it.coverImageUri.startsWith("file:") || it.coverImageUri.startsWith("/") || it.coverImageUri.getOrNull(1) == ':' }

    override suspend fun updateCoverImageUriIfUnchanged(
        journalId: Uuid,
        previous: String,
        coverImageUri: String,
    ) {
        val existing = journals[journalId]?.takeIf { it.coverImageUri == previous } ?: return
        journals[journalId] = existing.copy(coverImageUri = coverImageUri)
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
