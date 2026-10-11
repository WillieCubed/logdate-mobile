package app.logdate.wear.reminders

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderBridgingTest {
    @Test
    fun `phone notifications still bridge except the phone's own reminders`() {
        val context = mockk<Context> { every { packageName } returns "studio.hypertext.logdate" }

        val config = reminderBridgingConfig(context)

        assertTrue(config.isBridgingEnabled)
        assertEquals(setOf("logdate.reminder"), config.excludedTags.orEmpty())
    }
}
