package app.logdate.client.domain.moments

import app.logdate.client.domain.editor.SaveEntryUseCase
import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.repository.journals.JournalNote
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkEditorSaveTest {
    @Test
    fun `editor resolves mark with durable note IDs before deleting draft`() =
        runTest {
            val add = mockk<AddNoteUseCase>()
            val drafts = mockk<DeleteEntryDraftUseCase>()
            val order = mutableListOf<String>()
            val at = Instant.parse("2026-10-12T12:00:00Z")
            val note = JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = "A moment")
            val markId = Uuid.random()
            val draftId = Uuid.random()
            coEvery { add.invoke(any<List<JournalNote>>(), any(), any(), any()) } coAnswers { order += "saved" }
            coEvery { drafts.deleteAfterPublish(draftId) } coAnswers { order += "draft removed" }
            val save =
                SaveEntryUseCase(add, drafts, linkVisitMemory = { _, _ -> }, fillMarkedMoment = { id, notes ->
                    assertEquals(markId, id)
                    assertEquals(listOf(note.uid), notes.map { it.uid })
                    order += "mark filled"
                })
            save(listOf(note), emptyList(), draftId, markId = markId.toString())
            assertEquals(listOf("saved", "mark filled", "draft removed"), order)
        }
}
