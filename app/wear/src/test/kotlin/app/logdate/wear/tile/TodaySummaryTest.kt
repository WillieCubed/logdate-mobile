package app.logdate.wear.tile

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.capture.Feeling
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TodaySummaryTest {
    @Test
    fun `the mood comes from today's newest check-in`() {
        val summary =
            summarizeToday(
                listOf(
                    text("#mood:sad Feeling sad", minute = 0),
                    text("#mood:great Feeling great", minute = 20),
                ),
            )

        assertEquals(Feeling.GREAT, summary.feeling)
    }

    @Test
    fun `a check-in is never the latest entry`() {
        val lunch = text("Lunch by the water", minute = 0)

        val summary = summarizeToday(listOf(lunch, text("#mood:good Feeling good", minute = 20)))

        assertEquals(lunch, summary.latestEntry)
    }

    @Test
    fun `every note counts as one of today's entries`() {
        val summary = summarizeToday(listOf(text("Lunch by the water", minute = 0), text("#mood:good Feeling good", minute = 20)))

        assertEquals(2, summary.entryCount)
    }

    @Test
    fun `an empty day has no mood and no latest entry`() {
        val summary = summarizeToday(emptyList())

        assertEquals(0, summary.entryCount)
        assertNull(summary.feeling)
        assertNull(summary.latestEntry)
    }

    @Test
    fun `the redesigned tile counts moments without counting feeling check ins`() {
        val lunch = text("Lunch by the water", minute = 0)
        val summary = summarizeMomentsToday(listOf(lunch, text("#mood:good Feeling good", minute = 20)))
        assertEquals(1, summary.entryCount)
        assertEquals(lunch, summary.latestEntry)
    }

    @Test
    fun `a redesigned day of feelings alone has no moments`() {
        val summary = summarizeMomentsToday(listOf(text("#mood:good Feeling good", minute = 20)))
        assertEquals(0, summary.entryCount)
        assertNull(summary.latestEntry)
    }

    private fun text(
        content: String,
        minute: Int,
    ): JournalNote.Text {
        val at = Instant.fromEpochMilliseconds(1_710_000_000_000 + minute * 60_000L)
        return JournalNote.Text(creationTimestamp = at, lastUpdated = at, content = content)
    }
}
