package app.logdate.ui.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * iOS-backed haptics. Each generator is held for the lifetime of the controller so the system
 * keeps the Taptic Engine primed between adjacent events (e.g. a tab swap followed by a
 * confirmation). `prepare()` is called immediately before each fire so cold first-touches
 * don't experience the ~100ms warm-up delay.
 */
private class IosPlatformHapticsController : PlatformHapticsController {
    private val selectionGenerator = UISelectionFeedbackGenerator()
    private val notificationGenerator = UINotificationFeedbackGenerator()
    private val impactLight = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val impactMedium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val impactHeavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val impactSoft = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft)
    private val impactRigid = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)

    override fun selection() {
        selectionGenerator.prepare()
        selectionGenerator.selectionChanged()
    }

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
}

actual fun createPlatformHapticsController(): PlatformHapticsController = IosPlatformHapticsController()
