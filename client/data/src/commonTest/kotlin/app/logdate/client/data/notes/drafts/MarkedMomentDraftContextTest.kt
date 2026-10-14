package app.logdate.client.data.notes.drafts

import app.logdate.client.data.fakes.FakeLocalEntryDraftStore
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MarkedMomentDraftContextTest {
    @Test
    fun `draft retains mark association through updates and repository restart`() =
        runTest {
            val store = FakeLocalEntryDraftStore()
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val repository = OfflineFirstEntryDraftRepository(store, CoroutineScope(dispatcher))
            val id = Uuid.random()
            val mark = Uuid.random().toString()
            val at = Clock.System.now()
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "Remember this")
            repository.createDraft(id, listOf(note), emptyList(), emptyList(), null, mark)
            repository.setSelectedJournalIds(id, emptyList())
            val restarted = OfflineFirstEntryDraftRepository(store, CoroutineScope(dispatcher))
            assertEquals(
                mark,
                restarted
                    .getDraft(id)
                    .first()
                    .getOrThrow()
                    .markId,
            )
            restarted.updateDraft(id, listOf(note.copy(content = "More context")), emptyList(), emptyList(), null, mark)
            assertEquals(
                mark,
                restarted
                    .getDraft(id)
                    .first()
                    .getOrThrow()
                    .markId,
            )
        }
}
