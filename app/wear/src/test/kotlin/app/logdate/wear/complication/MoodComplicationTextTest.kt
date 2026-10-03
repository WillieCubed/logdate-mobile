package app.logdate.wear.complication

import app.logdate.client.repository.journals.JournalNote
import app.logdate.wear.capture.Feeling
import app.logdate.wear.capture.MomentNoteCodec
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MoodComplicationTextTest {
    @Test
    fun `a check-in saved by the check-in screen shows its emoji`() {
        val checkIn = JournalNote.Text(creationTimestamp = NOON, lastUpdated = NOON, content = "#mood:great Feeling great")

        assertEquals("😄", feelingComplicationText(MomentNoteCodec.latestFeeling(listOf(checkIn))))
    }

    @Test
    fun `sad and rough show the faces the check-in screen offers`() {
        assertEquals("😔", feelingComplicationText(Feeling.SAD))
        assertEquals("😰", feelingComplicationText(Feeling.ROUGH))
    }

    @Test
    fun `a day without a check-in shows a dash`() {
        assertEquals("—", feelingComplicationText(null))
    }

    private companion object {
        val NOON: Instant = Instant.fromEpochMilliseconds(1_710_000_000_000)
    }
}
