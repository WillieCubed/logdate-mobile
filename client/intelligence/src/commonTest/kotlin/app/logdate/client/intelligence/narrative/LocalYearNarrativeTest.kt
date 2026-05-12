package app.logdate.client.intelligence.narrative

import app.logdate.shared.model.ActivityType
import app.logdate.shared.model.NarrativeOrigin
import app.logdate.shared.model.Rewind
import app.logdate.shared.model.RewindMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class LocalYearNarrativeTest {
    private val now = Clock.System.now()

    private fun weekRewind(
        activities: List<ActivityType> = emptyList(),
        offsetDays: Int = 0,
    ): Rewind =
        Rewind(
            uid = Uuid.random(),
            startDate = now + offsetDays.days,
            endDate = now + (offsetDays + 7).days,
            generationDate = now,
            label = "wk",
            title = "Week",
            content = emptyList(),
            metadata =
                RewindMetadata(
                    detectedActivities = activities,
                    locationSummary = null,
                    milestones = emptyList(),
                    peopleHighlighted = emptyList(),
                ),
        )

    @Test
    fun `empty weekly rewinds produces empty chapters and no themes`() {
        val narrative = buildLocalYearNarrative(year = 2026, weeklyRewinds = emptyList())
        assertEquals(emptyList(), narrative.chapters)
        assertEquals(emptyList(), narrative.yearThemes)
        assertEquals(NarrativeOrigin.LOCAL_HEURISTIC, narrative.origin)
    }

    @Test
    fun `tags narrative with local origin`() {
        val narrative =
            buildLocalYearNarrative(
                year = 2026,
                weeklyRewinds = listOf(weekRewind(listOf(ActivityType.TRAVEL))),
            )
        assertEquals(NarrativeOrigin.LOCAL_HEURISTIC, narrative.origin)
    }

    @Test
    fun `aggregates top themes from weekly activities`() {
        val rewinds =
            listOf(
                weekRewind(listOf(ActivityType.TRAVEL), 0),
                weekRewind(listOf(ActivityType.TRAVEL), 7),
                weekRewind(listOf(ActivityType.TRAVEL), 14),
                weekRewind(listOf(ActivityType.SOCIAL), 21),
            )
        val narrative = buildLocalYearNarrative(2026, rewinds)
        assertTrue(narrative.yearThemes.isNotEmpty())
        assertEquals("travel", narrative.yearThemes.first())
        assertTrue(narrative.yearThemes.contains("social"))
    }

    @Test
    fun `produces four chapters covering every weekly rewind`() {
        val rewinds = (0 until 12).map { weekRewind(listOf(ActivityType.MIXED), offsetDays = it * 7) }
        val narrative = buildLocalYearNarrative(2026, rewinds)
        assertEquals(4, narrative.chapters.size)
        // Together, the chapters' key-week indices should cover all 12 weeks.
        val coveredIndices = narrative.chapters.flatMap { it.keyWeekIndices }
        assertEquals((0 until 12).toList(), coveredIndices)
    }

    @Test
    fun `every overall narrative mentions the week count`() {
        val rewinds = (0 until 5).map { weekRewind(listOf(ActivityType.MIXED), offsetDays = it * 7) }
        val narrative = buildLocalYearNarrative(2026, rewinds)
        assertTrue(narrative.overallNarrative.contains("5"))
    }
}
