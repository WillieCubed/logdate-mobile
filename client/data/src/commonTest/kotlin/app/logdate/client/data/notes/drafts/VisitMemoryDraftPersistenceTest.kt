package app.logdate.client.data.notes.drafts

import app.logdate.client.data.fakes.FakeLocalEntryDraftStore
import app.logdate.client.repository.journals.EntryDraft
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.location.VisitMemoryContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class VisitMemoryDraftPersistenceTest {
    @Test
    fun `visit context and original creation time survive restart and draft update`() =
        runTest {
            val store = FakeLocalEntryDraftStore()
            val first = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val draftId = Uuid.parse("00000000-0000-0000-0000-000000000071")
            val created = Instant.parse("2026-09-29T22:00:00Z")
            val visit = VisitMemoryContext("observation", "cafe", "Mothership Coffee", 36.1, -115.1, Instant.parse("2026-09-28T16:00:00Z"))
            val note =
                JournalNote.Text(
                    uid = Uuid.random(),
                    content = "Remembering yesterday",
                    creationTimestamp = created,
                    lastUpdated = created,
                )
            first.createDraft(draftId, listOf(note), emptyList(), emptyList(), visit)
            val reopened = OfflineFirstEntryDraftRepository(store, backgroundScope)
            val loaded = reopened.getDraft(draftId).first().getOrThrow()
            assertEquals(visit, loaded.visitContext)
            val serialized = Json.encodeToString(EntryDraft.serializer(), loaded)
            assertEquals(visit, Json.decodeFromString(EntryDraft.serializer(), serialized).visitContext)
            assertEquals(created, loaded.notes.single().creationTimestamp)
            val changed = note.copy(content = "A little more about yesterday")
            reopened.updateDraft(draftId, listOf(changed), emptyList(), emptyList(), visit)
            assertEquals(visit, store.getDraft(draftId)?.visitContext)
            assertEquals("A little more about yesterday", (store.getDraft(draftId)?.notes?.single() as JournalNote.Text).content)
        }
}
