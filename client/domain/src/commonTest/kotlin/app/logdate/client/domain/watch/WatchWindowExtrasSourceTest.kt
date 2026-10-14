package app.logdate.client.domain.watch

import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindContent
import app.logdate.shared.model.user.UserData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class WatchWindowExtrasSourceTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val note = Uuid.random()
    private val favorite = Uuid.random()
    private val narrative = Uuid.random()
    private val rewinds = MutableStateFlow(emptyList<Rewind>())
    private val user = MutableStateFlow(UserData(favoriteNotes = listOf(favorite.toString(), "bad", favorite.toString())))
    private val weekStart = MutableStateFlow<DayOfWeek?>(DayOfWeek.MONDAY)
    private val source =
        WatchWindowExtrasSource(
            rewinds,
            user,
            weekStart,
            object : Clock {
                override fun now() = now
            },
            { TimeZone.UTC },
        )

    @Test
    fun currentWeeklyRewindUsesOnlyRealNoteSourcesAndFavorites() =
        runTest {
            rewinds.value = listOf(rewind("2026-10-05T00:00:00Z", "2026-10-12T00:00:00Z"))
            assertEquals(WindowExtraIds(rewind = setOf(note), starred = setOf(favorite)), source.observe().first())
        }

    @Test
    fun oldAndAnnualRewindsDoNotFillTheWatch() =
        runTest {
            rewinds.value =
                listOf(rewind("2026-09-28T00:00:00Z", "2026-10-05T00:00:00Z"), rewind("2026-01-01T00:00:00Z", "2026-10-12T00:00:00Z"))
            assertEquals(emptySet(), source.observe().first().rewind)
        }

    @Test
    fun changedWeekPreferenceAndFavoriteRemovalTakeEffect() =
        runTest {
            rewinds.value = listOf(rewind("2026-10-04T00:00:00Z", "2026-10-11T00:00:00Z"))
            assertEquals(emptySet(), source.observe().first().rewind)
            weekStart.value = DayOfWeek.SUNDAY
            user.value = UserData()
            assertEquals(WindowExtraIds(rewind = setOf(note)), source.observe().first())
        }

    private fun rewind(
        start: String,
        end: String,
    ) = Rewind(
        uid = Uuid.random(),
        startDate = Instant.parse(start),
        endDate = Instant.parse(end),
        generationDate = now,
        label = "2026#41",
        title = "This week",
        content =
            listOf(
                RewindContent.TextNote(now, note, "A real note"),
                RewindContent.NarrativeContext(now, narrative, "A generated panel"),
            ),
    )
}
