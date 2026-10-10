@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package app.logdate.server.logdate

import app.logdate.server.sync.InMemorySyncRepository
import app.logdate.shared.model.sync.DeviceId
import app.logdate.shared.model.sync.JournalMergeRequest
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class JournalMergeDraftReferencesTest {
    @Test
    fun `existing and late drafts expose surviving journal references without changing encrypted blocks`() =
        runTest {
            val drafts = mutableMapOf<Pair<UUID, String>, LogDateDraft>()
            val backend =
                object : LogDateCollectionsRepository by SyncBackedLogDateCollectionsRepository(InMemorySyncRepository()) {
                    override suspend fun upsertDraft(
                        userId: UUID,
                        draft: LogDateDraft,
                    ): LogDateDraft {
                        drafts[userId to draft.id] = draft
                        return draft
                    }

                    override suspend fun getDraft(
                        userId: UUID,
                        id: String,
                    ): LogDateDraft? = drafts[userId to id]

                    override suspend fun draftChanges(
                        userId: UUID,
                        since: Long,
                        limit: Int,
                    ): LogDateChangeSet<LogDateDraft, LogDateDraftDeletion> =
                        LogDateChangeSet(drafts.filterKeys { it.first == userId }.values.toList(), emptyList(), 1L)
                }
            val repository = MergeAwareLogDateCollectionsRepository(backend, InMemoryJournalMergeStore())
            val user = UUID.randomUUID()
            val source = journal()
            val destination = journal()
            repository.upsertJournal(user, source)
            repository.upsertJournal(user, destination)
            val draft =
                LogDateDraft(
                    Uuid.random().toString(),
                    "encrypted text",
                    listOf("TEXT"),
                    listOf(source.id, destination.id),
                    1L,
                    1L,
                    0L,
                    DeviceId("device"),
                    1,
                    "LDSE2:opaque-blocks",
                )
            repository.upsertDraft(user, draft)
            repository.merge(user, source.id, JournalMergeRequest(Uuid.random().toString(), destination.id, emptyList()))
            val restored = assertNotNull(repository.getDraft(user, draft.id))
            assertEquals(listOf(destination.id), restored.journalIds)
            assertEquals(draft.encryptedBlocks, restored.encryptedBlocks)
            assertEquals(
                listOf(destination.id),
                repository
                    .draftChanges(user, 0L, 100)
                    .changes
                    .single()
                    .journalIds,
            )
            val later = repository.upsertDraft(user, draft.copy(id = Uuid.random().toString()))
            assertEquals(listOf(destination.id), later.journalIds)
            assertEquals(draft.encryptedBlocks, later.encryptedBlocks)
        }

    private fun journal() = LogDateJournal(Uuid.random().toString(), "Journal", "Description", 1, 1, 0, DeviceId("device"))

    private fun association(
        journal: String,
        content: String,
    ) = LogDateAssociation(journal, content, 1, 0, DeviceId("device"))
}
