package app.logdate.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins down the contract between semantic events and the underlying primitives. This is the
 * single source of truth for what each event "feels like" — change a mapping here and a
 * test breaks, which is the point.
 */
class LogDateHapticsTest {
    @Test
    fun `selection event maps to selection primitive`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.selection()

        assertEquals(listOf<RecordedHaptic>(RecordedHaptic.Selection), recorder.events)
    }

    @Test
    fun `saveSucceeded fires success notification`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.saveSucceeded()

        assertEquals(
            listOf<RecordedHaptic>(RecordedHaptic.Notification(HapticNotificationType.Success)),
            recorder.events,
        )
    }

    @Test
    fun `recordingStarted fires medium impact - the press is decisive`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.recordingStarted()

        assertEquals(
            listOf<RecordedHaptic>(RecordedHaptic.Impact(HapticImpactStrength.Medium)),
            recorder.events,
        )
    }

    @Test
    fun `recordingCancelled fires warning notification`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.recordingCancelled()

        assertEquals(
            listOf<RecordedHaptic>(RecordedHaptic.Notification(HapticNotificationType.Warning)),
            recorder.events,
        )
    }

    @Test
    fun `confirmDestruction fires heavy impact`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.confirmDestruction()

        assertEquals(
            listOf<RecordedHaptic>(RecordedHaptic.Impact(HapticImpactStrength.Heavy)),
            recorder.events,
        )
    }

    @Test
    fun `rewindEndReached fires a celebration pattern`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.rewindEndReached()

        assertEquals(1, recorder.events.size, "rewindEndReached should fire one composed pattern")
        val event = recorder.events.single()
        assertTrue(event is RecordedHaptic.Pattern, "expected pattern, got $event")
        // The celebration should crescendo — last step is a success.
        assertEquals(
            HapticStep.Notification(HapticNotificationType.Success),
            event.spec.steps.last(),
        )
    }

    @Test
    fun `autoSaved is suppressed by default - silent background save`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.autoSaved()

        assertTrue(
            recorder.events.isEmpty(),
            "autoSaved should fire nothing by default — it must feel invisible. Got: ${recorder.events}",
        )
    }

    @Test
    fun `toggle on differs from toggle off so users can hear the state in their hand`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.toggle(on = true)
        haptics.toggle(on = false)

        assertEquals(2, recorder.events.size)
        assertTrue(
            recorder.events[0] != recorder.events[1],
            "toggle(on=true) and toggle(on=false) must produce distinct feel; both were ${recorder.events[0]}",
        )
    }

    @Test
    fun `reduce-motion controller suppresses non-critical events`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder, reduceMotion = { true })

        haptics.selection()
        haptics.toolSelected()
        haptics.pageTurned()

        assertTrue(
            recorder.events.isEmpty(),
            "selection/toolSelected/pageTurned must not fire when reduce-motion is on. Got: ${recorder.events}",
        )
    }

    @Test
    fun `reduce-motion lambda is consulted on every fire so live system changes take effect`() {
        var reduce = false
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder, reduceMotion = { reduce })

        haptics.selection()
        reduce = true
        haptics.selection()
        reduce = false
        haptics.selection()

        assertEquals(
            2,
            recorder.events.size,
            "reduceMotion must be polled on every fire, not captured at construction",
        )
    }

    @Test
    fun `reduce-motion controller still fires safety-critical events`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder, reduceMotion = { true })

        haptics.error()
        haptics.warning()
        haptics.confirmDestruction()
        haptics.recordingStarted()

        assertEquals(
            4,
            recorder.events.size,
            "Critical events must always fire — got ${recorder.events.size}: ${recorder.events}",
        )
    }
}

/** Records every primitive call so tests can assert exact mapping behavior. */
internal class RecordingPlatformHaptics : PlatformHapticsController {
    val events = mutableListOf<RecordedHaptic>()

    override fun selection() {
        events += RecordedHaptic.Selection
    }

    override fun impact(strength: HapticImpactStrength) {
        events += RecordedHaptic.Impact(strength)
    }

    override fun notification(type: HapticNotificationType) {
        events += RecordedHaptic.Notification(type)
    }

    override fun tick() {
        events += RecordedHaptic.Tick
    }

    override fun pattern(spec: HapticPattern) {
        events += RecordedHaptic.Pattern(spec)
    }
}

internal sealed interface RecordedHaptic {
    data object Selection : RecordedHaptic

    data object Tick : RecordedHaptic

    data class Impact(
        val strength: HapticImpactStrength,
    ) : RecordedHaptic

    data class Notification(
        val type: HapticNotificationType,
    ) : RecordedHaptic

    data class Pattern(
        val spec: HapticPattern,
    ) : RecordedHaptic
}
