package app.logdate.feature.rewind.ui.overview

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class RewindLaunchScopeTest {
    @Test
    fun `weekly launch hides previously generated annual rewinds`() {
        val weekly = history("Week 36")
        val annual = history("2025")

        assertEquals(listOf(weekly), listOf(weekly, annual).forWeeklyLaunch())
    }

    private fun history(label: String) =
        RewindHistoryUiState(
            uid = Uuid.random(),
            title = label,
            label = label,
            startDate = LocalDate(2025, 9, 1),
            endDate = LocalDate(2025, 9, 7),
            message = "A week",
        )
}
