package app.logdate.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class HapticPatternTest {
    @Test
    fun `pattern of single step holds that step`() {
        val pattern = HapticPattern.of(HapticStep.Tick)

        assertEquals(listOf(HapticStep.Tick), pattern.steps)
    }

    @Test
    fun `pattern preserves step order and inter-step delays`() {
        val pattern =
            HapticPattern.of(
                HapticStep.Tick,
                HapticStep.Wait(millis = 50),
                HapticStep.Notification(HapticNotificationType.Success),
            )

        assertEquals(
            listOf(
                HapticStep.Tick,
                HapticStep.Wait(millis = 50),
                HapticStep.Notification(HapticNotificationType.Success),
            ),
            pattern.steps,
        )
    }

    @Test
    fun `wait step rejects negative duration`() {
        try {
            HapticStep.Wait(millis = -1)
            error("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }
}
