package app.logdate.wear.notification

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [WearNotificationScheduler], responsible for the intelligent delivery of
 * proactive engagement prompts on Wear OS.
 *
 * The test suite ensures that notification types are correctly resolved based on the time
 * of day, that the generated content is contextually appropriate (e.g., morning greetings
 * vs. evening reflections), and that strict cooldown logic prevents over-notifying the user
 * within a single day.
 */
class WearNotificationSchedulerTest {
    // =======================================================================
    // Prompt type resolution
    // =======================================================================

    @Test
    fun `morning prompt at 8am`() {
        val type = PromptType.forHour(8)
        assertEquals(PromptType.MORNING, type)
    }

    @Test
    fun `morning prompt at 10am`() {
        val type = PromptType.forHour(10)
        assertEquals(PromptType.MORNING, type)
    }

    @Test
    fun `evening prompt at 9pm`() {
        val type = PromptType.forHour(21)
        assertEquals(PromptType.EVENING, type)
    }

    @Test
    fun `evening prompt at 11pm`() {
        val type = PromptType.forHour(23)
        assertEquals(PromptType.EVENING, type)
    }

    @Test
    fun `no prompt during midday`() {
        val type = PromptType.forHour(14)
        assertEquals(PromptType.NONE, type)
    }

    @Test
    fun `no prompt during night`() {
        val type = PromptType.forHour(3)
        assertEquals(PromptType.NONE, type)
    }

    // =======================================================================
    // Notification content
    // =======================================================================

    @Test
    fun `morning title is greeting`() {
        val content = PromptContent.forType(PromptType.MORNING)
        assertTrue(content.title.contains("morning", ignoreCase = true))
    }

    @Test
    fun `evening title is reflection`() {
        val content = PromptContent.forType(PromptType.EVENING)
        assertTrue(content.title.isNotEmpty())
    }

    // =======================================================================
    // Cooldown logic
    // =======================================================================

    @Test
    fun `cooldown prevents duplicate morning prompt`() {
        val tracker = PromptCooldownTracker()
        tracker.markSent(PromptType.MORNING, dayEpoch = 100)

        assertFalse(tracker.canSend(PromptType.MORNING, dayEpoch = 100))
    }

    @Test
    fun `cooldown allows morning prompt on new day`() {
        val tracker = PromptCooldownTracker()
        tracker.markSent(PromptType.MORNING, dayEpoch = 100)

        assertTrue(tracker.canSend(PromptType.MORNING, dayEpoch = 101))
    }

    @Test
    fun `cooldown allows evening after morning same day`() {
        val tracker = PromptCooldownTracker()
        tracker.markSent(PromptType.MORNING, dayEpoch = 100)

        assertTrue(tracker.canSend(PromptType.EVENING, dayEpoch = 100))
    }

    @Test
    fun `cooldown prevents duplicate evening prompt`() {
        val tracker = PromptCooldownTracker()
        tracker.markSent(PromptType.EVENING, dayEpoch = 100)

        assertFalse(tracker.canSend(PromptType.EVENING, dayEpoch = 100))
    }
}
