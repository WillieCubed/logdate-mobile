package app.logdate.feature.library.fakes

import app.logdate.client.repository.journals.JournalContentRepository
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Journal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * Fake implementation of [JournalContentRepository] for testing.
 */
class FakeJournalContentRepository : JournalContentRepository {
    private val links = MutableStateFlow<Map<Uuid, Set<Uuid>>>(emptyMap())
    private val journals = MutableStateFlow<Map<Uuid, Journal>>(emptyMap())

    fun setJournalsForContent(
        contentId: Uuid,
        journalList: List<Journal>,
    ) {
        journals.value = journals.value + journalList.associateBy { it.id }
        links.value = links.value + (contentId to journalList.map { it.id }.toSet())
    }

    override fun observeContentForJournal(journalId: Uuid): Flow<List<JournalNote>> = MutableStateFlow(emptyList())

    override fun observeJournalsForContent(contentId: Uuid): Flow<List<Journal>> =
        links.map { linkMap ->
            val journalIds = linkMap[contentId] ?: emptySet()
            journalIds.mapNotNull { journals.value[it] }
        }

    override suspend fun addContentToJournal(
        contentId: Uuid,
        journalId: Uuid,
    ) {
        val current = links.value[contentId] ?: emptySet()
        links.value = links.value + (contentId to (current + journalId))
    }

    override suspend fun removeContentFromJournal(
        contentId: Uuid,
        journalId: Uuid,
    ) {
        val current = links.value[contentId] ?: return
        links.value = links.value + (contentId to (current - journalId))
    }

    override suspend fun addContentToJournals(
        contentId: Uuid,
        journalIds: List<Uuid>,
    ) {
        val current = links.value[contentId] ?: emptySet()
        links.value = links.value + (contentId to (current + journalIds))
    }

    override suspend fun removeContentFromAllJournals(contentId: Uuid) {
        links.value = links.value - contentId
    }
}
