package app.logdate.client.intelligence.rewind.local

import app.logdate.shared.model.ActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeActivityMapperTest {
    @Test
    fun `empty themes returns MIXED`() {
        assertEquals(listOf(ActivityType.MIXED), deriveActivitiesFromThemes(emptyList()))
    }

    @Test
    fun `themes with no recognized keywords returns MIXED`() {
        assertEquals(
            listOf(ActivityType.MIXED),
            deriveActivitiesFromThemes(listOf("waffles", "purple", "elsewhere")),
        )
    }

    @Test
    fun `travel themes return TRAVEL`() {
        assertEquals(listOf(ActivityType.TRAVEL), deriveActivitiesFromThemes(listOf("Vacation in Spain")))
        assertTrue(deriveActivitiesFromThemes(listOf("flight to Tokyo")).contains(ActivityType.TRAVEL))
        assertTrue(deriveActivitiesFromThemes(listOf("road trip")).contains(ActivityType.TRAVEL))
    }

    @Test
    fun `social themes return SOCIAL`() {
        assertEquals(
            listOf(ActivityType.SOCIAL),
            deriveActivitiesFromThemes(listOf("dinner with friends")),
        )
    }

    @Test
    fun `work themes return FOCUSED_WORK`() {
        assertEquals(
            listOf(ActivityType.FOCUSED_WORK),
            deriveActivitiesFromThemes(listOf("project deadline")),
        )
    }

    @Test
    fun `quiet themes return QUIET`() {
        assertEquals(
            listOf(ActivityType.QUIET),
            deriveActivitiesFromThemes(listOf("restful weekend")),
        )
    }

    @Test
    fun `milestone keywords return MILESTONE`() {
        assertTrue(deriveActivitiesFromThemes(listOf("graduation day")).contains(ActivityType.MILESTONE))
        assertTrue(deriveActivitiesFromThemes(listOf("birthday")).contains(ActivityType.MILESTONE))
        assertTrue(deriveActivitiesFromThemes(listOf("anniversary")).contains(ActivityType.MILESTONE))
    }

    @Test
    fun `mixed themes return all matching activities`() {
        val result = deriveActivitiesFromThemes(listOf("travel", "social gathering", "work project"))
        assertTrue(result.contains(ActivityType.TRAVEL))
        assertTrue(result.contains(ActivityType.SOCIAL))
        assertTrue(result.contains(ActivityType.FOCUSED_WORK))
        assertEquals(3, result.size)
    }

    @Test
    fun `case is normalized for matching`() {
        val result = deriveActivitiesFromThemes(listOf("TRAVEL", "Friend", "WORK"))
        assertTrue(result.contains(ActivityType.TRAVEL))
        assertTrue(result.contains(ActivityType.SOCIAL))
        assertTrue(result.contains(ActivityType.FOCUSED_WORK))
    }

    @Test
    fun `duplicate matches are deduplicated`() {
        assertEquals(
            listOf(ActivityType.TRAVEL),
            deriveActivitiesFromThemes(listOf("vacation", "trip", "flight")),
        )
    }
}
