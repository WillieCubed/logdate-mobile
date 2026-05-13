package app.logdate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityMedium
import platform.UIKit.UIContentSizeCategoryDidChangeNotification
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraSmall
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryMedium
import platform.UIKit.UIContentSizeCategorySmall

/**
 * Apple's text-style ladder mapped onto Compose's Material 3 roles. Sizes track the iOS Human
 * Interface Guidelines, scaled by the user's current Dynamic Type preference so the app honors
 * Settings → Display & Brightness → Text Size (and the system-wide Accessibility text-size
 * larger sizes). `FontFamily.Default` resolves to SF Pro on iOS automatically.
 *
 * Letter-spacing is held at zero — Material's `0.4sp` micro-tracking reads as alien once the
 * rest of the surface is iOS-flavored. Sizes and line heights mirror the
 * Apple Human Interface Guidelines text-style table; weights pick `Semibold`
 * where Apple's UIKit defaults do (Title / Headline) and stay `Regular` elsewhere.
 */
private fun iosTypography(scale: Float): Typography {
    fun TextUnit.scaled(): TextUnit = (value * scale).sp
    return Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 57.sp.scaled(),
                lineHeight = 64.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 34.sp.scaled(),
                lineHeight = 41.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp.scaled(),
                lineHeight = 34.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp.scaled(),
                lineHeight = 34.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp.scaled(),
                lineHeight = 28.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp.scaled(),
                lineHeight = 25.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp.scaled(),
                lineHeight = 25.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp.scaled(),
                lineHeight = 22.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp.scaled(),
                lineHeight = 20.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 17.sp.scaled(),
                lineHeight = 22.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp.scaled(),
                lineHeight = 21.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp.scaled(),
                lineHeight = 20.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp.scaled(),
                lineHeight = 18.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp.scaled(),
                lineHeight = 16.sp.scaled(),
                letterSpacing = 0.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp.scaled(),
                lineHeight = 13.sp.scaled(),
                letterSpacing = 0.sp,
            ),
    )
}

@Composable
actual fun platformTypography(): Typography {
    val scale by rememberDynamicTypeScale()
    return iosTypography(scale)
}

/**
 * Observes the active Dynamic Type scale from `UIApplication.preferredContentSizeCategory` and
 * re-emits whenever the user changes Text Size in Settings (via the
 * `UIContentSizeCategoryDidChangeNotification` system notification). The returned [State]
 * triggers recomposition of any Composable that reads it.
 *
 * The scale factors mirror the body-style ratios published in the iOS Human Interface
 * Guidelines, capped at 3.12× for the largest accessibility size.
 */
@Composable
private fun rememberDynamicTypeScale(): State<Float> {
    val state = remember { mutableStateOf(currentDynamicTypeScale()) }
    DisposableEffect(Unit) {
        val observer =
            NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIContentSizeCategoryDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
                usingBlock = { state.value = currentDynamicTypeScale() },
            )
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
    return state
}

private fun currentDynamicTypeScale(): Float =
    dynamicTypeScaleFor(UIApplication.sharedApplication.preferredContentSizeCategory)

/**
 * Maps a [UIContentSizeCategory] name to the body-style scale factor used by the Apple HIG.
 * The default ([UIContentSizeCategoryLarge]) is 1.0; values fall back to 1.0 for any category
 * we don't recognize (forward-compatible if Apple introduces new sizes).
 */
internal fun dynamicTypeScaleFor(category: String?): Float =
    when (category) {
        UIContentSizeCategoryExtraSmall -> 0.82f
        UIContentSizeCategorySmall -> 0.88f
        UIContentSizeCategoryMedium -> 0.94f
        UIContentSizeCategoryLarge -> 1.0f
        UIContentSizeCategoryExtraLarge -> 1.12f
        UIContentSizeCategoryExtraExtraLarge -> 1.24f
        UIContentSizeCategoryExtraExtraExtraLarge -> 1.35f
        UIContentSizeCategoryAccessibilityMedium -> 1.64f
        UIContentSizeCategoryAccessibilityLarge -> 1.94f
        UIContentSizeCategoryAccessibilityExtraLarge -> 2.35f
        UIContentSizeCategoryAccessibilityExtraExtraLarge -> 2.76f
        UIContentSizeCategoryAccessibilityExtraExtraExtraLarge -> 3.12f
        else -> 1.0f
    }
