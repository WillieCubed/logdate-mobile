package app.logdate.feature.onboarding.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3Api::class)
class BirthdaySelectableDatesTest {
    private val today = LocalDate(2026, 9, 18)
    private val dates = BirthdaySelectableDates(today)

    @Test
    fun `yesterday and earlier can be a birthday`() {
        assertTrue(dates.isSelectableDate(LocalDate(2026, 9, 17).utcMillis()))
        assertTrue(dates.isSelectableDate(LocalDate(1990, 1, 1).utcMillis()))
    }

    @Test
    fun `today and later cannot be a birthday`() {
        assertFalse(dates.isSelectableDate(today.utcMillis()))
        assertFalse(dates.isSelectableDate(LocalDate(2030, 1, 1).utcMillis()))
        assertFalse(dates.isSelectableYear(2027))
    }

    private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}
