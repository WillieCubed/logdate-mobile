package app.logdate.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Resolves iOS semantic neutrals against the requested theme.
 *
 * Apple's `UIColor.system*` class methods aren't exposed by the current Kotlin/Native UIKit
 * binding as plain property accesses (only `blackColor` / `whiteColor` are), so the values
 * here mirror the documented hex codes from the Apple Human Interface Guidelines colour
 * reference. A later pass can swap in a UIKit-driven resolution that also picks up Increase
 * Contrast / Reduce Transparency accessibility settings.
 *
 * Brand colors (primary / tint) intentionally stay null so the LogDate purple keeps its role
 * as the brand accent.
 */
@Composable
actual fun rememberPlatformSemanticColors(darkTheme: Boolean): PlatformSemanticColors? =
    remember(darkTheme) {
        if (darkTheme) DarkIosSemantics else LightIosSemantics
    }

private val LightIosSemantics =
    PlatformSemanticColors(
        background = Color(0xFFFFFFFF),
        secondaryBackground = Color(0xFFF2F2F7),
        tertiaryBackground = Color(0xFFFFFFFF),
        groupedBackground = Color(0xFFF2F2F7),
        label = Color(0xFF000000),
        // 60% black-on-light, per HIG secondary label
        secondaryLabel = Color(0x993C3C43),
        tertiaryLabel = Color(0x4D3C3C43),
        // ~29% opacity separator
        separator = Color(0x4A3C3C43),
        tint = null,
    )

private val DarkIosSemantics =
    PlatformSemanticColors(
        background = Color(0xFF000000),
        secondaryBackground = Color(0xFF1C1C1E),
        tertiaryBackground = Color(0xFF2C2C2E),
        groupedBackground = Color(0xFF000000),
        label = Color(0xFFFFFFFF),
        secondaryLabel = Color(0x99EBEBF5),
        tertiaryLabel = Color(0x4DEBEBF5),
        separator = Color(0xA6545458),
        tint = null,
    )
