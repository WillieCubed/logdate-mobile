package app.logdate.wear.v2.haptics

import app.logdate.ui.platform.DefaultLogDateHaptics
import app.logdate.ui.platform.LogDateHaptics
import app.logdate.ui.platform.PlatformHapticsController

/**
 * The haptics the new watch app plays. With [HapticsPreference.ESSENTIAL], or while the system's
 * touch haptics are off, only the events the shared vocabulary marks critical play. Both are read
 * on every event, so a change applies to the next one.
 */
fun watchHaptics(
    controller: PlatformHapticsController,
    preferences: HapticsPreferenceStore,
    systemHapticsOff: () -> Boolean,
): LogDateHaptics =
    DefaultLogDateHaptics(controller) {
        preferences.preference.value == HapticsPreference.ESSENTIAL || systemHapticsOff()
    }
