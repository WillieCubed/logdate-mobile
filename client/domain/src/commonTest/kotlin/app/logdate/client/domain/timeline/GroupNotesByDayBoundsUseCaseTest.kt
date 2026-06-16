@file:Suppress("ktlint:standard:function-naming")

package app.logdate.client.domain.timeline

import app.logdate.client.health.model.DayBounds
import app.logdate.client.repository.journals.JournalNote
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests for [GroupNotesByDayBoundsUseCase], which handles the complex logic of grouping
 * journal entries into logical "days" based on user-defined or sleep-aware boundaries.
 *
 * These tests verify:
 * - Attribution of late-night notes to the correct logical day (e.g., 2 AM entry belonging to the previous day).
 * - Robustness when sleep data is missing or discontinuous (ensuring no notes are "lost" in gaps).
 * - Fallback behavior when health data is unavailable.
 * - Correct handling of skipped sleep or irregular sleep patterns.
 */
class GroupNotesByDayBoundsUseCaseTest {
    private val timezone = TimeZone.UTC
    private val date = LocalDate(2025, 3, 15)

    @Test
    fun `empty notes returns empty map`() =
        runTest {
            val useCase = createUseCase(sleepEnabled = true)
            val result = useCase(emptyList(), timezone)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `settings disabled uses calendar date grouping`() =
        runTest {
            val note1am = textNote("Late night", localInstant(2025, 3, 15, 1, 30))
            val note10am = textNote("Morning", localInstant(2025, 3, 15, 10, 0))

            val useCase = createUseCase(sleepEnabled = false)
            val result = useCase(listOf(note1am, note10am), timezone)

            assertEquals(1, result.size)
            assertEquals(2, result[date]?.size)
        }

    @Test
    fun `late night note assigned to previous day when sleep bounds active`() =
        runTest {
            val march14 = LocalDate(2025, 3, 14)
            val march15 = LocalDate(2025, 3, 15)

            val bounds =
                mapOf(
                    march14 to
                        DayBounds(
                            start = localInstant(2025, 3, 14, 7, 0),
                            end = localInstant(2025, 3, 15, 2, 0),
                        ),
                    march15 to
                        DayBounds(
                            start = localInstant(2025, 3, 15, 8, 0),
                            end = localInstant(2025, 3, 16, 2, 0),
                        ),
                )

            val note130am = textNote("Late night out", localInstant(2025, 3, 15, 1, 30))
            val note10am = textNote("Morning coffee", localInstant(2025, 3, 15, 10, 0))

            val useCase = createUseCase(sleepEnabled = true, bounds = bounds)
            val result = useCase(listOf(note130am, note10am), timezone)

            assertEquals(setOf(march14, march15), result.keys)
            assertEquals(1, result[march14]?.size, "March 14 should have the late-night note")
            assertEquals("Late night out", (result[march14]?.first() as JournalNote.Text).content)
            assertEquals(1, result[march15]?.size, "March 15 should have the morning note")
        }

    @Test
    fun `fixed day-start hour groups a midnight note under the previous day when sleep disabled`() =
        runTest {
            val march14 = LocalDate(2025, 3, 14)
            val march15 = LocalDate(2025, 3, 15)
            val bounds =
                mapOf(
                    march14 to DayBounds(localInstant(2025, 3, 14, 3, 0), localInstant(2025, 3, 15, 3, 0)),
                    march15 to DayBounds(localInstant(2025, 3, 15, 3, 0), localInstant(2025, 3, 16, 3, 0)),
                )

            // A note created just past midnight belongs to the previous day under a 3 AM boundary.
            val midnightNote = textNote("Just past midnight", localInstant(2025, 3, 15, 0, 0))
            val morningNote = textNote("Morning coffee", localInstant(2025, 3, 15, 10, 0))

            // Sleep schedule is OFF, but the user explicitly set a 3 AM day-start hour.
            val useCase = createUseCase(sleepEnabled = false, bounds = bounds, dayStartHour = 3)
            val result = useCase(listOf(midnightNote, morningNote), timezone)

            assertEquals(setOf(march14, march15), result.keys)
            assertEquals("Just past midnight", (result[march14]?.single() as JournalNote.Text).content)
            assertEquals("Morning coffee", (result[march15]?.single() as JournalNote.Text).content)
        }

    @Test
    fun `early morning note after wakeup assigned to current day`() =
        runTest {
            val march15 = LocalDate(2025, 3, 15)

            val bounds =
                mapOf(
                    march15 to
                        DayBounds(
                            start = localInstant(2025, 3, 15, 5, 30),
                            end = localInstant(2025, 3, 16, 1, 0),
                        ),
                )

            val note6am = textNote("Early run", localInstant(2025, 3, 15, 6, 0))
            val useCase = createUseCase(sleepEnabled = true, bounds = bounds)
            val result = useCase(listOf(note6am), timezone)

            assertEquals(1, result[march15]?.size)
        }

    // --- Real-world scenarios ---

    @Test
    fun `new user sleeps one night then skips — entries land on correct days`() =
        runTest {
            val march15 = LocalDate(2025, 3, 15)
            val march16 = LocalDate(2025, 3, 16)
            val march17 = LocalDate(2025, 3, 17)

            // Day 1: entry at 10am, user sleeps 11pm Mar 15 → 7am Mar 16
            // Day 2: entry at 10am, user does NOT sleep
            // Day 3: entry at 10am
            //
            // Expected bounds:
            //   Mar 15: [4am Mar 15, 11pm Mar 15)  — no prior wake-up, sleep found
            //   Mar 16: [7am Mar 16, 4am Mar 17)   — wake-up from sleep, no bedtime
            //   Mar 17: [4am Mar 17, 4am Mar 18)   — no sleep data at all
            val bounds =
                mapOf(
                    march15 to
                        DayBounds(
                            start = localInstant(2025, 3, 15, 4, 0), // default (no prior sleep)
                            end = localInstant(2025, 3, 15, 23, 0), // bedtime found
                        ),
                    march16 to
                        DayBounds(
                            start = localInstant(2025, 3, 16, 7, 0), // wake-up found
                            end = localInstant(2025, 3, 17, 4, 0), // default (didn't sleep)
                        ),
                    march17 to
                        DayBounds(
                            start = localInstant(2025, 3, 17, 4, 0), // default (no wake-up)
                            end = localInstant(2025, 3, 18, 4, 0), // default (no bedtime)
                        ),
                )

            val entry1 = textNote("Day 1 entry", localInstant(2025, 3, 15, 10, 0))
            val entry2 = textNote("Day 2 entry", localInstant(2025, 3, 16, 10, 0))
            val entry3 = textNote("Day 3 entry", localInstant(2025, 3, 17, 10, 0))

            val useCase = createUseCase(sleepEnabled = true, bounds = bounds)
            val result = useCase(listOf(entry1, entry2, entry3), timezone)

            assertEquals(3, result.size, "Should produce exactly 3 days")
            assertEquals("Day 1 entry", (result[march15]!!.single() as JournalNote.Text).content)
            assertEquals("Day 2 entry", (result[march16]!!.single() as JournalNote.Text).content)
            assertEquals("Day 3 entry", (result[march17]!!.single() as JournalNote.Text).content)
        }

    @Test
    fun `late night entry on skipped-sleep day extends into next calendar date`() =
        runTest {
            val march16 = LocalDate(2025, 3, 16)
            val march17 = LocalDate(2025, 3, 17)

            // User didn't sleep the night of Mar 16 — day extends to 4am Mar 17
            val bounds =
                mapOf(
                    march16 to
                        DayBounds(
                            start = localInstant(2025, 3, 16, 7, 0),
                            end = localInstant(2025, 3, 17, 4, 0),
                        ),
                    march17 to
                        DayBounds(
                            start = localInstant(2025, 3, 17, 4, 0),
                            end = localInstant(2025, 3, 18, 4, 0),
                        ),
                )

            // Entry at 3am on Mar 17 — still "yesterday" since user hasn't slept
            val lateEntry = textNote("Still awake", localInstant(2025, 3, 17, 3, 0))
            val useCase = createUseCase(sleepEnabled = true, bounds = bounds)
            val result = useCase(listOf(lateEntry), timezone)

            assertEquals(
                march16,
                result.keys.single(),
                "3am entry should belong to March 16 since user didn't sleep",
            )
        }

    // --- Fallback robustness: every note must always land in a valid day ---

    @Test
    fun `note in sleep gap between days is assigned to nearest day never lost`() =
        runTest {
            val march15 = LocalDate(2025, 3, 15)
            val march16 = LocalDate(2025, 3, 16)

            // Day 15 ends at 1am, day 16 starts at 7am — 6-hour gap (sleep window)
            val bounds =
                mapOf(
                    march15 to
                        DayBounds(
                            start = localInstant(2025, 3, 15, 7, 0),
                            end = localInstant(2025, 3, 16, 1, 0),
                        ),
                    march16 to
                        DayBounds(
                            start = localInstant(2025, 3, 16, 7, 0),
                            end = localInstant(2025, 3, 17, 1, 0),
                        ),
                )

            // Note at 3am falls in the gap — no day's bounds contain it
            val noteInGap = textNote("Can't sleep", localInstant(2025, 3, 16, 3, 0))
            val useCase = createUseCase(sleepEnabled = true, bounds = bounds)
            val result = useCase(listOf(noteInGap), timezone)

            // The note must appear somewhere — it must not be lost
            val totalNotes = result.values.sumOf { it.size }
            assertEquals(1, totalNotes, "Note in the sleep gap must not be lost")
        }

    @Test
    fun `all notes are preserved when grouping with bounds — none lost`() =
        runTest {
            val march14 = LocalDate(2025, 3, 14)
            val march15 = LocalDate(2025, 3, 15)

            val bounds =
                mapOf(
                    march14 to
                        DayBounds(
                            start = localInstant(2025, 3, 14, 7, 0),
                            end = localInstant(2025, 3, 15, 2, 0),
                        ),
                    march15 to
                        DayBounds(
                            start = localInstant(2025, 3, 15, 8, 0),
                            end = localInstant(2025, 3, 16, 2, 0),
                        ),
                )

            val notes =
                listOf(
                    textNote("Early AM", localInstant(2025, 3, 15, 1, 0)),
                    textNote("In the gap", localInstant(2025, 3, 15, 3, 0)),
                    textNote("Morning", localInstant(2025, 3, 15, 9, 0)),
                    textNote("Afternoon", localInstant(2025, 3, 15, 14, 0)),
                    textNote("Late night", localInstant(2025, 3, 15, 23, 30)),
                )

            val useCase = createUseCase(sleepEnabled = true, bounds = bounds)
            val result = useCase(notes, timezone)

            val totalNotes = result.values.sumOf { it.size }
            assertEquals(
                notes.size,
                totalNotes,
                "Every input note must appear exactly once in the output",
            )
        }

    @Test
    fun `health repository failure falls back to default bounds notes still grouped`() =
        runTest {
            val note = textNote("Morning", localInstant(2025, 3, 15, 10, 0))

            // Health repo throws on every call
            val settingsRepo = FakeDayBoundarySettingsRepository(sleepEnabled = true)
            val healthRepo =
                FakeHealthRepository().apply {
                    throwable = RuntimeException("Health Connect unavailable")
                }
            val getDayBounds = GetDayBoundsUseCase(healthRepo, settingsRepo)
            val useCase = GroupNotesByDayBoundsUseCase(getDayBounds, settingsRepo, FakeBoundaryPreferences())

            val result = useCase(listOf(note), timezone)

            // Even with health errors, the note must land somewhere
            val totalNotes = result.values.sumOf { it.size }
            assertEquals(1, totalNotes, "Note must not be lost even when health repo fails")
        }

    @Test
    fun `no sleep data and no user preferences still produces valid grouping`() =
        runTest {
            // No bounds configured, no sleep data — pure default (4am boundary)
            val notes =
                listOf(
                    textNote("Late night", localInstant(2025, 3, 15, 2, 0)),
                    textNote("Morning", localInstant(2025, 3, 15, 10, 0)),
                    textNote("Next day early", localInstant(2025, 3, 16, 3, 0)),
                )

            val useCase = createUseCase(sleepEnabled = true, bounds = emptyMap())
            val result = useCase(notes, timezone)

            val totalNotes = result.values.sumOf { it.size }
            assertEquals(
                notes.size,
                totalNotes,
                "All notes must be preserved under default fallback bounds",
            )

            // With 4am default boundary: 2am on the 15th and 3am on the 16th
            // should each fall into the previous day's bounds
            for ((date, dayNotes) in result) {
                assertTrue(
                    dayNotes.isNotEmpty(),
                    "Day $date should have at least one note",
                )
            }
        }

    @Test
    fun `single note is never assigned to a nonexistent date`() =
        runTest {
            val note = textNote("Solo note", localInstant(2025, 3, 15, 22, 0))
            val useCase = createUseCase(sleepEnabled = true, bounds = emptyMap())
            val result = useCase(listOf(note), timezone)

            assertEquals(1, result.size, "Exactly one day should exist")
            assertEquals(1, result.values.single().size, "The single note must be in that day")
        }

    // --- Helpers ---

    private fun localInstant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Instant = LocalDateTime(year, month, day, hour, minute).toInstant(timezone)

    private fun textNote(
        content: String,
        timestamp: Instant,
    ) = JournalNote.Text(
        uid = Uuid.random(),
        content = content,
        creationTimestamp = timestamp,
        lastUpdated = timestamp,
    )

    private fun createUseCase(
        sleepEnabled: Boolean,
        bounds: Map<LocalDate, DayBounds> = emptyMap(),
        dayStartHour: Int? = null,
    ): GroupNotesByDayBoundsUseCase {
        val settingsRepo = FakeDayBoundarySettingsRepository(sleepEnabled)
        val healthRepo = FakeHealthRepository().apply { boundsByDate = bounds }
        return GroupNotesByDayBoundsUseCase(
            GetDayBoundsUseCase(healthRepo, settingsRepo),
            settingsRepo,
            FakeBoundaryPreferences(dayStartHour),
        )
    }
}
