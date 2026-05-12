package app.logdate.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HapticEventTest {
    @Test
    fun `every event is classified as critical or non-critical exactly once`() {
        val all = HapticEvent.entries.toSet()
        val critical = HapticEvent.criticalEvents

        assertTrue(critical.isNotEmpty(), "expected at least one critical event")
        assertTrue(
            critical.all { it in all },
            "criticalEvents references undefined entries: ${critical - all}",
        )
    }

    @Test
    fun `safety-relevant events are classified critical`() {
        val expectedCritical =
            setOf(
                HapticEvent.RecordingStarted,
                HapticEvent.RecordingFinished,
                HapticEvent.RecordingCancelled,
                HapticEvent.Warning,
                HapticEvent.ConfirmDestruction,
                HapticEvent.Error,
                HapticEvent.SyncConflictSurfaced,
            )

        assertEquals(
            expectedCritical,
            HapticEvent.criticalEvents,
            "Critical-event allowlist must match the design spec — these are the only events " +
                "that fire when the system has reduced/disabled haptics.",
        )
    }

    @Test
    fun `routine non-critical events are not in the critical set`() {
        val nonCritical =
            listOf(
                HapticEvent.Selection,
                HapticEvent.AutoSaved,
                HapticEvent.RewindCardCentered,
                HapticEvent.PageTurned,
                HapticEvent.ToolSelected,
            )

        for (event in nonCritical) {
            assertTrue(
                event !in HapticEvent.criticalEvents,
                "$event should be suppressible when system reduce-motion is on.",
            )
        }
    }
}
