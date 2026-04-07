package app.logdate.client.domain.rewind

import app.logdate.shared.model.ActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [deriveActivitiesFromThemes], which maps narrative themes
 * to structured [ActivityType] values used in rewind metadata.
 */
class DeriveActivitiesFromThemesTest {
    @Test
    fun `empty themes returns MIXED`() {
        val result = deriveActivitiesFromThemes(emptyList())

        assertEquals(listOf(ActivityType.MIXED), result)
    }

    @Test
    fun `themes with no recognized keywords returns MIXED`() {
        val result = deriveActivitiesFromThemes(listOf("waffles", "purple", "elsewhere"))

        assertEquals(listOf(ActivityType.MIXED), result)
    }

    @Test
    fun `travel themes return TRAVEL activity`() {
        val result = deriveActivitiesFromThemes(listOf("Vacation in Spain"))

        assertEquals(listOf(ActivityType.TRAVEL), result)
    }

    @Test
    fun `flight and road keywords also return TRAVEL`() {
        assertTrue(deriveActivitiesFromThemes(listOf("flight to Tokyo")).contains(ActivityType.TRAVEL))
        assertTrue(deriveActivitiesFromThemes(listOf("road trip")).contains(ActivityType.TRAVEL))
        assertTrue(deriveActivitiesFromThemes(listOf("trip to Maine")).contains(ActivityType.TRAVEL))
    }

    @Test
    fun `social themes return SOCIAL activity`() {
        val result = deriveActivitiesFromThemes(listOf("dinner with friends"))

        assertEquals(listOf(ActivityType.SOCIAL), result)
    }

    @Test
    fun `work themes return FOCUSED_WORK activity`() {
        val result = deriveActivitiesFromThemes(listOf("project deadline"))

        assertEquals(listOf(ActivityType.FOCUSED_WORK), result)
    }

    @Test
    fun `quiet themes return QUIET activity`() {
        val result = deriveActivitiesFromThemes(listOf("restful weekend"))

        assertEquals(listOf(ActivityType.QUIET), result)
    }

    @Test
    fun `milestone themes return MILESTONE activity`() {
        val result = deriveActivitiesFromThemes(listOf("graduation day"))

        assertEquals(listOf(ActivityType.MILESTONE), result)
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
        val result = deriveActivitiesFromThemes(listOf("vacation", "trip", "flight"))

        assertEquals(listOf(ActivityType.TRAVEL), result)
    }

    @Test
    fun `birthday and anniversary count as milestones`() {
        assertTrue(deriveActivitiesFromThemes(listOf("birthday")).contains(ActivityType.MILESTONE))
        assertTrue(deriveActivitiesFromThemes(listOf("anniversary")).contains(ActivityType.MILESTONE))
    }
}
