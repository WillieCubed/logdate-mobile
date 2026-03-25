package app.logdate.client.feature.widgets

import app.logdate.client.domain.recommendation.MemoryRecallData
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnThisDayWidgetStateMapperTest {
    @Test
    fun `toWidgetState maps date to ISO string`() {
        val data =
            MemoryRecallData(
                date = LocalDate(2025, 3, 24),
                summary = "Trip to the park",
            )

        val state = data.toWidgetState()

        assertEquals("2025-03-24", state.dateIso)
    }

    @Test
    fun `toWidgetState formats date for display`() {
        val data =
            MemoryRecallData(
                date = LocalDate(2025, 3, 24),
                summary = "Trip to the park",
            )

        val state = data.toWidgetState()

        assertEquals("March 24, 2025", state.dateFormatted)
    }

    @Test
    fun `toWidgetState preserves summary text`() {
        val data =
            MemoryRecallData(
                date = LocalDate(2025, 1, 1),
                summary = "New Year's Day celebration",
            )

        val state = data.toWidgetState()

        assertEquals("New Year's Day celebration", state.summary)
    }

    @Test
    fun `toWidgetState uses provided fallback when summary is empty`() {
        val data =
            MemoryRecallData(
                date = LocalDate(2025, 6, 15),
                summary = "",
            )

        val state = data.toWidgetState(fallbackSummary = "A memory from this day")

        assertEquals("A memory from this day", state.summary)
    }

    @Test
    fun `toWidgetState uses empty string when summary is empty and no fallback`() {
        val data =
            MemoryRecallData(
                date = LocalDate(2025, 6, 15),
                summary = "",
            )

        val state = data.toWidgetState()

        assertEquals("", state.summary)
    }

    @Test
    fun `toWidgetState picks first media URI as thumbnail`() {
        val data =
            MemoryRecallData(
                date = LocalDate(2025, 7, 4),
                summary = "Fireworks",
                mediaUris = listOf("content://media/1", "content://media/2"),
            )

        val state = data.toWidgetState()

        assertEquals("content://media/1", state.thumbnailUri)
    }

    @Test
    fun `toWidgetState sets null thumbnail when no media`() {
        val data =
            MemoryRecallData(
                date = LocalDate(2025, 12, 25),
                summary = "Holiday",
                mediaUris = emptyList(),
            )

        val state = data.toWidgetState()

        assertNull(state.thumbnailUri)
    }
}
