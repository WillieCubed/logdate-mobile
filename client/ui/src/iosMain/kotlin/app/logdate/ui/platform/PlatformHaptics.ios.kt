package app.logdate.ui.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * iOS-backed haptics. Each generator is held for the lifetime of the controller so the system
 * keeps the Taptic Engine primed between adjacent events (e.g. a recording start followed by a
 * save confirmation). `prepare()` is called immediately before each fire so cold first-touches
 * don't experience the ~100ms warm-up delay.
 */
private class IosPlatformHapticsController : PlatformHapticsController {
    private val notificationGenerator = UINotificationFeedbackGenerator()
    private val impactLight = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val impactMedium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val impactHeavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val impactSoft = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft)
    private val impactRigid = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)

    override fun impact(strength: HapticImpactStrength) {
        val generator =
            when (strength) {
                HapticImpactStrength.Light -> impactLight
                HapticImpactStrength.Medium -> impactMedium
                HapticImpactStrength.Heavy -> impactHeavy
                HapticImpactStrength.Soft -> impactSoft
                HapticImpactStrength.Rigid -> impactRigid
            }
        generator.prepare()
        generator.impactOccurred()
    }

    override fun notification(type: HapticNotificationType) {
        val style =
            when (type) {
                HapticNotificationType.Success -> UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
                HapticNotificationType.Warning -> UINotificationFeedbackType.UINotificationFeedbackTypeWarning
                HapticNotificationType.Error -> UINotificationFeedbackType.UINotificationFeedbackTypeError
            }
        notificationGenerator.prepare()
        notificationGenerator.notificationOccurred(style)
    }

    /**
     * Lighter than [HapticImpactStrength.Light] — uses the soft generator at half intensity so
     * high-rate ticks (Rewind snap, settings slider) feel like a quiet detent, not a tap.
     */
    override fun tick() {
        impactSoft.prepare()
        impactSoft.impactOccurredWithIntensity(0.5)
    }
}

actual fun createPlatformHapticsController(): PlatformHapticsController = IosPlatformHapticsController()
