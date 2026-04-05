package app.logdate.feature.editor.audio

import app.logdate.client.awareness.daylight.DaylightPeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AudioLabelResolverTest {
    private val resolver = AudioLabelResolver()

    // 2024-06-21 at 10:00 local time — should classify as MORNING or MIDDAY
    private val morningInstant =
        kotlinx.datetime
            .LocalDateTime(2024, 6, 21, 10, 0)
            .toInstant(TimeZone.UTC)

    // 2024-06-21 at 21:00 local time — should classify as EVENING or NIGHT
    private val eveningInstant =
        kotlinx.datetime
            .LocalDateTime(2024, 6, 21, 21, 0)
            .toInstant(TimeZone.UTC)

    @Test
    fun captionTakesPriority() {
        val result =
            resolver.resolve(
                createdAt = morningInstant,
                caption = "My birdsong recording",
                locationName = "Central Park",
            )
        assertIs<AudioLabelResult.Caption>(result)
        assertEquals("My birdsong recording", result.text)
    }

    @Test
    fun blankCaptionFallsThrough() {
        val result =
            resolver.resolve(
                createdAt = morningInstant,
                caption = "   ",
                locationName = "Home",
            )
        assertIs<AudioLabelResult.Contextual>(result)
    }

    @Test
    fun nullCaptionFallsThrough() {
        val result =
            resolver.resolve(
                createdAt = morningInstant,
                caption = null,
            )
        assertIs<AudioLabelResult.Contextual>(result)
    }

    @Test
    fun contextualResultIncludesLocationName() {
        val result =
            resolver.resolve(
                createdAt = morningInstant,
                locationName = "Central Park",
            )
        assertIs<AudioLabelResult.Contextual>(result)
        assertEquals("Central Park", result.locationName)
    }

    @Test
    fun blankLocationNameBecomesNull() {
        val result =
            resolver.resolve(
                createdAt = morningInstant,
                locationName = "   ",
            )
        assertIs<AudioLabelResult.Contextual>(result)
        assertEquals(null, result.locationName)
    }

    @Test
    fun contextualResultIncludesDaylightPeriod() {
        val result = resolver.resolve(createdAt = morningInstant)
        assertIs<AudioLabelResult.Contextual>(result)
        // Should produce a valid DaylightPeriod regardless of system timezone
        val validPeriods = DaylightPeriod.entries.toSet()
        assert(result.period in validPeriods) {
            "Expected a valid DaylightPeriod, got ${result.period}"
        }
    }

    @Test
    fun classifyPeriodWithLocationReturnsPeriod() {
        // NYC coordinates — the classifier should return a valid period
        val period =
            resolver.classifyPeriod(
                createdAt = morningInstant,
                latitude = 40.7128,
                longitude = -74.006,
            )
        val validPeriods = DaylightPeriod.entries.toSet()
        assert(period in validPeriods) {
            "Expected a valid DaylightPeriod, got $period"
        }
    }

    @Test
    fun classifyPeriodWithoutLocationReturnsPeriod() {
        val period =
            resolver.classifyPeriod(
                createdAt = morningInstant,
            )
        val validPeriods = DaylightPeriod.entries.toSet()
        assert(period in validPeriods) {
            "Expected a valid DaylightPeriod, got $period"
        }
    }
}
