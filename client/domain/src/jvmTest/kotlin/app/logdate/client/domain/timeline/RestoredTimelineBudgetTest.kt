package app.logdate.client.domain.timeline

import app.logdate.client.domain.entities.ExtractPeopleUseCase
import app.logdate.client.intelligence.AIResult
import app.logdate.client.intelligence.MAX_AI_INPUT_CHARACTERS
import app.logdate.client.repository.journals.JournalNote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Instant

class RestoredTimelineBudgetTest {
    @Test
    fun `combined restored text above the enrichment budget keeps original entries without concatenating input`() =
        runTest {
            verifyBudget(MAX_AI_INPUT_CHARACTERS / 2, MAX_AI_INPUT_CHARACTERS / 2, expectedCalls = 0)
        }

    @Test
    fun `joined text exactly at the budget still enriches the day`() =
        runTest {
            verifyBudget(MAX_AI_INPUT_CHARACTERS / 2, MAX_AI_INPUT_CHARACTERS / 2 - 1, expectedCalls = 1)
        }

    @Test
    fun `day previews preserve trimmed text while bounding allocations`() {
        val now = Instant.parse("2026-10-16T12:00:00Z")
        for (content in listOf("  first\nsecond  ", " ".repeat(100_000) + "a".repeat(200) + "  ", " \t\n")) {
            val note = JournalNote.Text(content = content, creationTimestamp = now, lastUpdated = now)
            assertEquals(content.trim().take(120), extractDayParts(listOf(note)).single().description)
            assertSame(content, note.content)
        }
    }

    private suspend fun verifyBudget(
        firstLength: Int,
        secondLength: Int,
        expectedCalls: Int,
    ) {
        val people = mockk<ExtractPeopleUseCase>()
        coEvery { people(any(), any()) } returns AIResult.Success(emptyList())
        val summary = mockk<SummarizeJournalEntriesUseCase>()
        coEvery { summary(any()) } returns SummarizeJournalEntriesResult.SummaryUnavailable
        val media = mockk<GetMediaUrisUseCase>()
        every { media(any()) } returns flowOf(emptyList())
        val moments = mockk<InferMomentsUseCase>()
        coEvery { moments(any(), any(), any()) } returns emptyList()
        val now = Instant.parse("2026-10-16T12:00:00Z")
        val first = JournalNote.Text(content = "a".repeat(firstLength), creationTimestamp = now, lastUpdated = now)
        val second = JournalNote.Text(content = "b".repeat(secondLength), creationTimestamp = now, lastUpdated = now)
        val day = GetTimelineDayUseCase(summary, media, people, moments)(LocalDate(2026, 10, 16), listOf(first, second), emptyList())
        coVerify(exactly = expectedCalls) { people(any(), any()) }
        assertEquals(2, day.entries.size)
        assertSame(first, day.entries.first())
        assertEquals(firstLength, first.content.length)
        assertEquals(secondLength, second.content.length)
    }
}
