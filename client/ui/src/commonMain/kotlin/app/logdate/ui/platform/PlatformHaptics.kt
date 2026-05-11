package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cross-platform haptic feedback. Maps loosely onto Apple's `UIImpactFeedbackGenerator`,
 * `UISelectionFeedbackGenerator`, and `UINotificationFeedbackGenerator`. On Android we route
 * through `HapticFeedbackConstants`; on desktop the controller is a no-op.
 *
 * Acquire via [rememberPlatformHaptics] inside Composables, or read [LocalPlatformHaptics]
 * directly when you need a stable instance to capture in a non-Composable handler.
 */
interface PlatformHapticsController {
    fun selection()

    fun impact(strength: HapticImpactStrength = HapticImpactStrength.Light)

    fun notification(type: HapticNotificationType)
}

enum class HapticImpactStrength { Light, Medium, Heavy, Soft, Rigid }

enum class HapticNotificationType { Success, Warning, Error }

/** Composition-local handle. Provided at the nav root; replaceable in previews/tests. */
val LocalPlatformHaptics =
    staticCompositionLocalOf<PlatformHapticsController> { NoOpPlatformHaptics }

@Composable
fun rememberPlatformHaptics(): PlatformHapticsController = LocalPlatformHaptics.current

internal object NoOpPlatformHaptics : PlatformHapticsController {
    override fun selection() = Unit

    override fun impact(strength: HapticImpactStrength) = Unit

    override fun notification(type: HapticNotificationType) = Unit
}

/**
 * Constructs the active platform haptics controller. iOS returns a real generator pool that
 * primes on first use; Android wraps `HapticFeedback`; desktop returns [NoOpPlatformHaptics].
 */
expect fun createPlatformHapticsController(): PlatformHapticsController
