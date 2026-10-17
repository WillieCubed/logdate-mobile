package app.logdate.feature.core.main

import app.logdate.client.domain.recommendation.HomeRecommendation
import app.logdate.ui.timeline.TimelineSuggestionBlock
import kotlin.test.Test
import kotlin.test.assertEquals

/** Home turns the activity recommendations into their timeline suggestions. */
class HomeActivitySuggestionMappingTest {
    @Test
    fun `a finished trip keeps its length and kind`() {
        assertEquals(
            TimelineSuggestionBlock.AfterActivity(minutes = 25, trip = true),
            HomeRecommendation.AfterActivity(minutes = 25, trip = true).toTimelineSuggestionBlock(),
        )
    }

    @Test
    fun `waking up becomes the morning start`() {
        assertEquals(TimelineSuggestionBlock.MorningStart, HomeRecommendation.MorningStart.toTimelineSuggestionBlock())
    }
}
