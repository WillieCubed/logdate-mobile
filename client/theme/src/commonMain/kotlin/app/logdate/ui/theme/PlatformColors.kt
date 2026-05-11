package app.logdate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Platform-derived semantic color tokens applied on top of [LightColorScheme] / [DarkColorScheme].
 *
 * On iOS the actual reads `UIColor.systemBackground`, `label`, `secondaryLabel`, `separator`,
 * `tintColor` so the resolved [ColorScheme] tracks the system theme and accent. On other
 * platforms the actual returns `null` and the base Material scheme is used unchanged.
 *
 * Returning `null` is the no-op path — keeps the Material 3 visual identity intact on Android
 * and desktop while iOS earns its native palette via N2.1.
 */
@Composable
expect fun rememberPlatformSemanticColors(darkTheme: Boolean): PlatformSemanticColors?

/** Tokens mirrored from Apple's semantic color set. Null entries fall back to Material defaults. */
data class PlatformSemanticColors(
    val background: androidx.compose.ui.graphics.Color? = null,
    val secondaryBackground: androidx.compose.ui.graphics.Color? = null,
    val tertiaryBackground: androidx.compose.ui.graphics.Color? = null,
    val groupedBackground: androidx.compose.ui.graphics.Color? = null,
    val label: androidx.compose.ui.graphics.Color? = null,
    val secondaryLabel: androidx.compose.ui.graphics.Color? = null,
    val tertiaryLabel: androidx.compose.ui.graphics.Color? = null,
    val separator: androidx.compose.ui.graphics.Color? = null,
    val tint: androidx.compose.ui.graphics.Color? = null,
)

/**
 * Layers [tokens] over [base], leaving any null token slot untouched. Centralizes the mapping
 * so the iOS theme actual stays a thin wiring file.
 */
fun ColorScheme.applyPlatformSemantics(tokens: PlatformSemanticColors?): ColorScheme {
    if (tokens == null) return this
    return copy(
        background = tokens.background ?: background,
        surface = tokens.secondaryBackground ?: surface,
        surfaceContainer = tokens.groupedBackground ?: surfaceContainer,
        surfaceContainerLow = tokens.tertiaryBackground ?: surfaceContainerLow,
        onBackground = tokens.label ?: onBackground,
        onSurface = tokens.label ?: onSurface,
        onSurfaceVariant = tokens.secondaryLabel ?: onSurfaceVariant,
        outline = tokens.separator ?: outline,
        outlineVariant = tokens.separator ?: outlineVariant,
        primary = tokens.tint ?: primary,
    )
}
