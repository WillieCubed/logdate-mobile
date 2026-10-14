package app.logdate.client.domain.moments

import app.logdate.client.domain.notes.AddNoteUseCase
import app.logdate.client.domain.notes.drafts.DeleteEntryDraftUseCase
import app.logdate.client.repository.journals.JournalNote
import app.logdate.client.repository.moments.MarkState
import app.logdate.client.repository.moments.MarkedMoment
import app.logdate.client.repository.moments.MarkedMomentRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlin.uuid.Uuid

class MarkedEntrySaveFactoryTest {
    private val add = mockk<AddNoteUseCase>()
    private val drafts = mockk<DeleteEntryDraftUseCase>()
    private val marks = mockk<MarkedMomentRepository>()
    private val id = Uuid.random()
    private val draftId = Uuid.random()
    private val captured = Instant.parse("2026-10-10T21:00:00Z")
    private val written = Instant.parse("2026-10-12T21:00:00Z")
    private val mark = MarkedMoment(id, captured, "America/Los_Angeles", null, null, MarkState.OPEN, "", emptySet(), "watch", captured, 0)
    private val note = JournalNote.Text(creationTimestamp = written, lastUpdated = written, content = "Remembered later")

    @Test
    fun `production save factory preserves original mark capture context`() =
        runTest {
            coEvery { marks.getMark(id) } returns mark
            coEvery { marks.fill(id, "", setOf(note.uid), any()) } returns true
            coEvery { add.invoke(any<List<JournalNote>>(), any(), any(), any()) } coAnswers {
                val saved = firstArg<List<JournalNote>>().single()
                assertEquals(captured, saved.creationTimestamp)
                assertEquals(mark.timeZoneId, saved.timeZoneId)
                assertEquals(written, saved.lastUpdated)
            }
            coEvery { drafts.deleteAfterPublish(draftId) } returns Unit
            markedEntrySaveUseCase(add, drafts, { _, _ -> }, marks)(listOf(note), emptyList(), draftId, markId = id.toString())
            coVerify(exactly = 1) { drafts.deleteAfterPublish(draftId) }
        }

    @Test
    fun `removed mark prevents publication and preserves draft`() =
        runTest {
            coEvery { marks.getMark(id) } returns null
            assertFailsWith<IllegalStateException> {
                markedEntrySaveUseCase(add, drafts, { _, _ -> }, marks)(listOf(note), emptyList(), draftId, markId = id.toString())
            }
            coVerify(exactly = 0) { add.invoke(any<List<JournalNote>>(), any(), any(), any()) }
            coVerify(exactly = 0) { drafts.deleteAfterPublish(any()) }
        }

    @Test
    fun `failed mark resolution preserves draft for retry after note save`() =
        runTest {
            coEvery { marks.getMark(id) } returns mark
            coEvery { marks.fill(id, "", setOf(note.uid), any()) } returns false
            coEvery { add.invoke(any<List<JournalNote>>(), any(), any(), any()) } returns Unit
            assertFailsWith<IllegalStateException> {
                markedEntrySaveUseCase(add, drafts, { _, _ -> }, marks)(listOf(note), emptyList(), draftId, markId = id.toString())
            }
            coVerify(exactly = 0) { drafts.deleteAfterPublish(any()) }
        }

    @Test
    fun `retry after mark resolution finishes retained draft without duplicating the resolution`() =
        runTest {
            coEvery { marks.getMark(id) } returns mark.copy(state = MarkState.FILLED, resolvedNoteIds = setOf(note.uid))
            coEvery { marks.fill(id, "", setOf(note.uid), any()) } returns false
            coEvery { add.invoke(any<List<JournalNote>>(), any(), any(), any()) } returns Unit
            coEvery { drafts.deleteAfterPublish(draftId) } returns Unit
            markedEntrySaveUseCase(add, drafts, { _, _ -> }, marks)(listOf(note), emptyList(), draftId, markId = id.toString())
            coVerify(exactly = 1) { drafts.deleteAfterPublish(draftId) }
        }
}
