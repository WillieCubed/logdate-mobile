package app.logdate.client.domain.fakes

import app.logdate.client.repository.journals.JournalRepository
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

/**
 * Minimal fake for testing use cases that depend on [JournalRepository].
 */
class FakeJournalRepository(
    initialJournals: List<Journal> = emptyList(),
) : JournalRepository {
    private val journalsFlow = MutableStateFlow(initialJournals)

    override val allJournalsObserved: Flow<List<Journal>> = journalsFlow

    fun setJournals(journals: List<Journal>) {
        journalsFlow.value = journals
    }

    override fun observeJournalById(id: Uuid): Flow<Journal> = throw NotImplementedError("Not needed for search tests")

    override suspend fun getJournalById(id: Uuid): Journal? = journalsFlow.value.find { it.id == id }

    override suspend fun create(journal: Journal): Uuid {
        journalsFlow.value = journalsFlow.value + journal
        return journal.id
    }

    override suspend fun update(journal: Journal) {
        journalsFlow.value = journalsFlow.value.map { if (it.id == journal.id) journal else it }
    }

    override suspend fun delete(journalId: Uuid) {
        journalsFlow.value = journalsFlow.value.filter { it.id != journalId }
    }

    override suspend fun saveDraft(draft: EditorDraft) {}

    override suspend fun getLatestDraft(): EditorDraft? = null

    override suspend fun getAllDrafts(): List<EditorDraft> = emptyList()

    override suspend fun getDraft(id: Uuid): EditorDraft? = null

    override suspend fun deleteDraft(id: Uuid) {}
}
