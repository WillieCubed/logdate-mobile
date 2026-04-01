package app.logdate.client.domain.streak

import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class CalculateStreakUseCaseTest {
    private val fixedTimeZone = TimeZone.UTC

    private fun noteOnDate(date: LocalDate): JournalNote.Text {
        val instant = date.atStartOfDayIn(fixedTimeZone)
        return JournalNote.Text(
            uid = Uuid.random(),
            creationTimestamp = instant,
            lastUpdated = instant,
            content = "Test note",
        )
    }

    private fun createUseCase(notesPerDay: Map<LocalDate, List<JournalNote>> = emptyMap()) =
        CalculateStreakUseCase(FakeNotesRepository(notesPerDay))

    @Test
    fun `streak is 0 when no entries exist`() =
        runTest {
            val useCase = createUseCase()
            val today = LocalDate(2026, 3, 15)
            assertEquals(0, useCase(today))
        }

    @Test
    fun `streak is 1 when only today has entries`() =
        runTest {
            val today = LocalDate(2026, 3, 15)
            val useCase =
                createUseCase(
                    notesPerDay = mapOf(today to listOf(noteOnDate(today))),
                )
            assertEquals(1, useCase(today))
        }

    @Test
    fun `streak counts consecutive days ending today`() =
        runTest {
            val today = LocalDate(2026, 3, 15)
            val yesterday = today.minus(1, DateTimeUnit.DAY)
            val twoDaysAgo = today.minus(2, DateTimeUnit.DAY)
            val useCase =
                createUseCase(
                    notesPerDay =
                        mapOf(
                            today to listOf(noteOnDate(today)),
                            yesterday to listOf(noteOnDate(yesterday)),
                            twoDaysAgo to listOf(noteOnDate(twoDaysAgo)),
                        ),
                )
            assertEquals(3, useCase(today))
        }

    @Test
    fun `streak breaks at gap day`() =
        runTest {
            val today = LocalDate(2026, 3, 15)
            val yesterday = today.minus(1, DateTimeUnit.DAY)
            val threeDaysAgo = today.minus(3, DateTimeUnit.DAY)
            val useCase =
                createUseCase(
                    notesPerDay =
                        mapOf(
                            today to listOf(noteOnDate(today)),
                            yesterday to listOf(noteOnDate(yesterday)),
                            threeDaysAgo to listOf(noteOnDate(threeDaysAgo)),
                        ),
                )
            assertEquals(2, useCase(today))
        }

    @Test
    fun `streak includes yesterday when today has no entries yet`() =
        runTest {
            val today = LocalDate(2026, 3, 15)
            val yesterday = today.minus(1, DateTimeUnit.DAY)
            val twoDaysAgo = today.minus(2, DateTimeUnit.DAY)
            val useCase =
                createUseCase(
                    notesPerDay =
                        mapOf(
                            yesterday to listOf(noteOnDate(yesterday)),
                            twoDaysAgo to listOf(noteOnDate(twoDaysAgo)),
                        ),
                )
            assertEquals(2, useCase(today))
        }

    @Test
    fun `streak is 0 when neither today nor yesterday has entries`() =
        runTest {
            val today = LocalDate(2026, 3, 15)
            val threeDaysAgo = today.minus(3, DateTimeUnit.DAY)
            val useCase =
                createUseCase(
                    notesPerDay =
                        mapOf(
                            threeDaysAgo to listOf(noteOnDate(threeDaysAgo)),
                        ),
                )
            assertEquals(0, useCase(today))
        }

    @Test
    fun `streak handles month boundary`() =
        runTest {
            val today = LocalDate(2026, 4, 2)
            val apr1 = today.minus(1, DateTimeUnit.DAY)
            val mar31 = today.minus(2, DateTimeUnit.DAY)
            val mar30 = today.minus(3, DateTimeUnit.DAY)
            val useCase =
                createUseCase(
                    notesPerDay =
                        mapOf(
                            today to listOf(noteOnDate(today)),
                            apr1 to listOf(noteOnDate(apr1)),
                            mar31 to listOf(noteOnDate(mar31)),
                            mar30 to listOf(noteOnDate(mar30)),
                        ),
                )
            assertEquals(4, useCase(today))
        }

    @Test
    fun `streak caps at max lookback`() =
        runTest {
            val today = LocalDate(2026, 3, 15)
            val notesPerDay = mutableMapOf<LocalDate, List<JournalNote>>()
            for (i in 0 until 400) {
                val date = today.minus(i, DateTimeUnit.DAY)
                notesPerDay[date] = listOf(noteOnDate(date))
            }
            val useCase = createUseCase(notesPerDay)
            assertEquals(365, useCase(today))
        }
}
