package app.logdate.wear.v2.haptics

import app.logdate.ui.platform.HapticImpactStrength
import app.logdate.ui.platform.HapticNotificationType
import app.logdate.ui.platform.PlatformHapticsController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test
import kotlin.test.assertEquals

class WatchHapticsTest {
    private val controller = PlayedHaptics()
    private val preferences = FakeHapticsPreferenceStore()
    private var systemHapticsOff = false
    private val haptics = watchHaptics(controller, preferences) { systemHapticsOff }

    @Test
    fun `All plays every event`() {
        haptics.detentTick()
        haptics.saveSucceeded()
        haptics.momentMarked()

        assertEquals(listOf("tick", "notification Success", "notification Success"), controller.played)
    }

    @Test
    fun `Essential drops crown ticks and save confirmations`() {
        preferences.set(HapticsPreference.ESSENTIAL)

        haptics.detentTick()
        haptics.saveSucceeded()

        assertEquals(emptyList<String>(), controller.played)
    }

    @Test
    fun `Essential still plays recording, mark and refusal events`() {
        preferences.set(HapticsPreference.ESSENTIAL)

        haptics.recordingLocked()
        haptics.recordingPaused()
        haptics.recordingResumed()
        haptics.momentMarked()
        haptics.limitApproaching()
        haptics.actionRejected()

        assertEquals(
            listOf(
                "impact Rigid",
                "impact Soft",
                "impact Light",
                "notification Success",
                "notification Warning",
                "notification Error",
            ),
            controller.played,
        )
    }

    @Test
    fun `system touch haptics off acts like Essential`() {
        systemHapticsOff = true

        haptics.detentTick()
        haptics.momentMarked()

        assertEquals(listOf("notification Success"), controller.played)
    }

    @Test
    fun `a changed setting applies to the next event`() {
        haptics.detentTick()
        preferences.set(HapticsPreference.ESSENTIAL)
        haptics.detentTick()
        preferences.set(HapticsPreference.ALL)
        haptics.detentTick()

        assertEquals(listOf("tick", "tick"), controller.played)
    }
}

private class PlayedHaptics : PlatformHapticsController {
    val played = mutableListOf<String>()

    override fun impact(strength: HapticImpactStrength) {
        played += "impact $strength"
    }

    override fun notification(type: HapticNotificationType) {
        played += "notification $type"
    }

    override fun tick() {
        played += "tick"
    }
}

private class FakeHapticsPreferenceStore : HapticsPreferenceStore {
    private val current = MutableStateFlow(HapticsPreference.ALL)

    override val preference: StateFlow<HapticsPreference> = current

    override fun set(preference: HapticsPreference) {
        current.value = preference
    }
}
