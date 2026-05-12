@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Wraps [content] with a fully-wired haptic stack — both the platform primitive controller
 * ([LocalPlatformHaptics]) and the semantic facade ([LocalLogDateHaptics]) — so any
 * descendant Composable can call `rememberLogDateHaptics().rewindEndReached()` and feel it.
 *
 * The OS reduce-motion / haptics-off setting is observed automatically; non-critical events
 * are suppressed when the user has disabled them at the system level.
 */
@Composable
fun LogDateHapticsProvider(content: @Composable () -> Unit) {
    val controller = rememberPlatformHapticsController()
    val reduceMotion = rememberSystemReduceMotion()
    val haptics = remember(controller) { DefaultLogDateHaptics(controller) { reduceMotion.value } }
    CompositionLocalProvider(
        LocalPlatformHaptics provides controller,
        LocalLogDateHaptics provides haptics,
    ) {
        content()
    }
}
