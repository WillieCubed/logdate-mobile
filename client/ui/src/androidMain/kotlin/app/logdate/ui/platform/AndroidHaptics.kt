package app.logdate.ui.platform

import android.content.Context

/**
 * Builds the vibrator-backed [PlatformHapticsController] for code that has a [Context] but no
 * composition, such as the watch app's dependency graph.
 */
fun createAndroidPlatformHapticsController(context: Context): PlatformHapticsController = AndroidPlatformHaptics(context.applicationContext)

/** Whether the user turned touch haptics off in system settings. Read it on every event. */
fun isSystemHapticFeedbackOff(context: Context): Boolean = isHapticDisabled(context)
