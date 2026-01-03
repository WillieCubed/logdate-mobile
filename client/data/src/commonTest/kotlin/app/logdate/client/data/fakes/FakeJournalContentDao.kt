package app.logdate.client.data.fakes

import app.logdate.client.database.dao.journals.JournalContentDao
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class FakeJournalContentDao : JournalContentDao {
    private val journalContent = mutableMapOf<Uuid, MutableSet<Uuid>>()
    private val contentJournals = mutableMapOf<Uuid, MutableSet<Uuid>>()
    private val state = MutableStateFlow<Map<Uuid, Set<Uuid>>>(emptyMap())

    override suspend fun addContentToJournal(link: JournalContentEntityLink) {
        val contentSet = journalContent.getOrPut(link.journalId) { mutableSetOf() }
        contentSet.add(link.contentId)

        val journalSet = contentJournals.getOrPut(link.contentId) { mutableSetOf() }
        journalSet.add(link.journalId)

        emitState()
    }

    override fun getContentForJournal(journalId: Uuid): Flow<List<Uuid>> {
        return state.map { it[journalId]?.toList() ?: emptyList() }
    }

    override fun getJournalsForContent(contentId: Uuid): Flow<List<Uuid>> {
        return state.map {
            contentJournals[contentId]?.toList() ?: emptyList()
        }
    }

    override suspend fun removeContentFromJournal(journalId: Uuid, contentId: Uuid) {
        journalContent[journalId]?.remove(contentId)
        if (journalContent[journalId].isNullOrEmpty()) {
            journalContent.remove(journalId)
        }

        contentJournals[contentId]?.remove(journalId)
        if (contentJournals[contentId].isNullOrEmpty()) {
            contentJournals.remove(contentId)
        }

        emitState()
    }

    override suspend fun removeContentFromAllJournals(contentId: Uuid) {
        contentJournals[contentId]?.forEach { journalId ->
            journalContent[journalId]?.remove(contentId)
            if (journalContent[journalId].isNullOrEmpty()) {
                journalContent.remove(journalId)
            }
        }
        contentJournals.remove(contentId)
        emitState()
    }

    override suspend fun isContentInJournal(journalId: Uuid, contentId: Uuid): Boolean {
        return journalContent[journalId]?.contains(contentId) ?: false
    }

    fun clear() {
        journalContent.clear()
        contentJournals.clear()
        emitState()
    }

    private fun emitState() {
        state.value = journalContent.mapValues { (_, value) -> value.toSet() }
    }
}
