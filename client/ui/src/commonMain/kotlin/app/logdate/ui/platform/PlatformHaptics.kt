package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cross-platform haptic feedback. Maps loosely onto Apple's `UIImpactFeedbackGenerator` and
 * `UINotificationFeedbackGenerator`. On Android we route through `Vibrator`/`VibrationEffect`;
 * on desktop the controller is a no-op.
 *
 * Acquire via [rememberPlatformHaptics] inside Composables, or read [LocalPlatformHaptics]
 * directly when you need a stable instance to capture in a non-Composable handler.
 */
interface PlatformHapticsController {
    fun impact(strength: HapticImpactStrength = HapticImpactStrength.Light)

    fun notification(type: HapticNotificationType)

    /**
     * A short, low-amplitude tick — lighter than [HapticImpactStrength.Light]. Used for high-rate
     * feedback like list-detent ticks and snap-to-center. Default falls back to a light impact so
     * platforms that haven't opted in still feel something.
     */
    fun tick() = impact(HapticImpactStrength.Light)
}

enum class HapticImpactStrength { Light, Medium, Heavy, Soft, Rigid }

enum class HapticNotificationType { Success, Warning, Error }

/** Composition-local handle. Provided at the nav root; replaceable in previews/tests. */
val LocalPlatformHaptics =
    staticCompositionLocalOf<PlatformHapticsController> { NoOpPlatformHaptics }

@Composable
fun rememberPlatformHaptics(): PlatformHapticsController = LocalPlatformHaptics.current

internal object NoOpPlatformHaptics : PlatformHapticsController {
    override fun impact(strength: HapticImpactStrength) = Unit

    override fun notification(type: HapticNotificationType) = Unit
}

/**
 * Constructs the active platform haptics controller. iOS returns a real generator pool that
 * primes on first use; Android currently returns [NoOpPlatformHaptics] (the real controller
 * needs a `Context` and is built via [rememberPlatformHapticsController] instead); desktop
 * returns [NoOpPlatformHaptics].
 *
 * Prefer [rememberPlatformHapticsController] inside Composables — it picks up platform
 * context (e.g. Android `LocalContext`) automatically.
 */
expect fun createPlatformHapticsController(): PlatformHapticsController

/**
 * Composable-aware factory. On Android this captures `LocalContext` to construct a real
 * [PlatformHapticsController] backed by the system `Vibrator`; on other platforms it
 * delegates to [createPlatformHapticsController]. Result is remembered for the
 * composition.
 */
@androidx.compose.runtime.Composable
expect fun rememberPlatformHapticsController(): PlatformHapticsController
