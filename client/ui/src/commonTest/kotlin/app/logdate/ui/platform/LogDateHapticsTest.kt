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
    fun `recordingFinished fires success notification`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.recordingFinished()

        assertEquals(
            listOf<RecordedHaptic>(RecordedHaptic.Notification(HapticNotificationType.Success)),
            recorder.events,
        )
    }

    @Test
    fun `transcriptionReady fires a tick`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder)

        haptics.transcriptionReady()

        assertEquals(
            listOf<RecordedHaptic>(RecordedHaptic.Tick),
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
    fun `reduce-motion controller suppresses non-critical events`() {
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder, reduceMotion = { true })

        haptics.saveSucceeded()
        haptics.transcriptionReady()

        assertTrue(
            recorder.events.isEmpty(),
            "saveSucceeded/transcriptionReady must not fire when reduce-motion is on. Got: ${recorder.events}",
        )
    }

    @Test
    fun `reduce-motion lambda is consulted on every fire so live system changes take effect`() {
        var reduce = false
        val recorder = RecordingPlatformHaptics()
        val haptics = DefaultLogDateHaptics(recorder, reduceMotion = { reduce })

        haptics.saveSucceeded()
        reduce = true
        haptics.saveSucceeded()
        reduce = false
        haptics.saveSucceeded()

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

        haptics.confirmDestruction()
        haptics.recordingStarted()
        haptics.recordingFinished()

        assertEquals(
            3,
            recorder.events.size,
            "Critical events must always fire — got ${recorder.events.size}: ${recorder.events}",
        )
    }
}

/** Records every primitive call so tests can assert exact mapping behavior. */
internal class RecordingPlatformHaptics : PlatformHapticsController {
    val events = mutableListOf<RecordedHaptic>()

    override fun impact(strength: HapticImpactStrength) {
        events += RecordedHaptic.Impact(strength)
    }

    override fun notification(type: HapticNotificationType) {
        events += RecordedHaptic.Notification(type)
    }

    override fun tick() {
        events += RecordedHaptic.Tick
    }
}

internal sealed interface RecordedHaptic {
    data object Tick : RecordedHaptic

    data class Impact(
        val strength: HapticImpactStrength,
    ) : RecordedHaptic

    data class Notification(
        val type: HapticNotificationType,
    ) : RecordedHaptic
}
