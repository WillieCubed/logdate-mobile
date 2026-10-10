@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.logdate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The light color scheme for the LogDate app when dynamic color is unavailable (Android 11, desktop,
 * iOS, or dynamic color turned off).
 *
 * Every role is set from the graphite palettes in `Color.kt`, following Material 3's tone mapping,
 * so no Material baseline purple shows through. Error roles stay at the Material defaults.
 */
val LightColorScheme =
    lightColorScheme(
        primary = GraphitePrimary40,
        onPrimary = GraphitePrimary100,
        primaryContainer = GraphitePrimary90,
        onPrimaryContainer = GraphitePrimary30,
        inversePrimary = GraphitePrimary80,
        secondary = GraphiteSecondary40,
        onSecondary = GraphiteSecondary100,
        secondaryContainer = GraphiteSecondary90,
        onSecondaryContainer = GraphiteSecondary30,
        tertiary = GraphiteTertiary40,
        onTertiary = GraphiteTertiary100,
        tertiaryContainer = GraphiteTertiary90,
        onTertiaryContainer = GraphiteTertiary30,
        background = GraphiteNeutral98,
        onBackground = GraphiteNeutral10,
        surface = GraphiteNeutral98,
        onSurface = GraphiteNeutral10,
        surfaceVariant = GraphiteNeutralVariant90,
        onSurfaceVariant = GraphiteNeutralVariant30,
        surfaceTint = GraphitePrimary40,
        inverseSurface = GraphiteNeutral20,
        inverseOnSurface = GraphiteNeutral95,
        outline = GraphiteNeutralVariant50,
        outlineVariant = GraphiteNeutralVariant80,
        scrim = GraphiteNeutral0,
        surfaceBright = GraphiteNeutral98,
        surfaceContainer = GraphiteNeutral94,
        surfaceContainerHigh = GraphiteNeutral92,
        surfaceContainerHighest = GraphiteNeutral90,
        surfaceContainerLow = GraphiteNeutral96,
        surfaceContainerLowest = GraphiteNeutral100,
        surfaceDim = GraphiteNeutral87,
        primaryFixed = GraphitePrimary90,
        primaryFixedDim = GraphitePrimary80,
        onPrimaryFixed = GraphitePrimary10,
        onPrimaryFixedVariant = GraphitePrimary30,
        secondaryFixed = GraphiteSecondary90,
        secondaryFixedDim = GraphiteSecondary80,
        onSecondaryFixed = GraphiteSecondary10,
        onSecondaryFixedVariant = GraphiteSecondary30,
        tertiaryFixed = GraphiteTertiary90,
        tertiaryFixedDim = GraphiteTertiary80,
        onTertiaryFixed = GraphiteTertiary10,
        onTertiaryFixedVariant = GraphiteTertiary30,
    )

/**
 * The dark color scheme for the LogDate app when dynamic color is unavailable.
 *
 * Built from the same graphite palettes as [LightColorScheme] with Material 3's dark tone mapping.
 */
val DarkColorScheme =
    darkColorScheme(
        primary = GraphitePrimary80,
        onPrimary = GraphitePrimary20,
        primaryContainer = GraphitePrimary30,
        onPrimaryContainer = GraphitePrimary90,
        inversePrimary = GraphitePrimary40,
        secondary = GraphiteSecondary80,
        onSecondary = GraphiteSecondary20,
        secondaryContainer = GraphiteSecondary30,
        onSecondaryContainer = GraphiteSecondary90,
        tertiary = GraphiteTertiary80,
        onTertiary = GraphiteTertiary20,
        tertiaryContainer = GraphiteTertiary30,
        onTertiaryContainer = GraphiteTertiary90,
        background = GraphiteNeutral6,
        onBackground = GraphiteNeutral90,
        surface = GraphiteNeutral6,
        onSurface = GraphiteNeutral90,
        surfaceVariant = GraphiteNeutralVariant30,
        onSurfaceVariant = GraphiteNeutralVariant80,
        surfaceTint = GraphitePrimary80,
        inverseSurface = GraphiteNeutral90,
        inverseOnSurface = GraphiteNeutral20,
        outline = GraphiteNeutralVariant60,
        outlineVariant = GraphiteNeutralVariant30,
        scrim = GraphiteNeutral0,
        surfaceBright = GraphiteNeutral24,
        surfaceContainer = GraphiteNeutral12,
        surfaceContainerHigh = GraphiteNeutral17,
        surfaceContainerHighest = GraphiteNeutral22,
        surfaceContainerLow = GraphiteNeutral10,
        surfaceContainerLowest = GraphiteNeutral4,
        surfaceDim = GraphiteNeutral6,
        primaryFixed = GraphitePrimary90,
        primaryFixedDim = GraphitePrimary80,
        onPrimaryFixed = GraphitePrimary10,
        onPrimaryFixedVariant = GraphitePrimary30,
        secondaryFixed = GraphiteSecondary90,
        secondaryFixedDim = GraphiteSecondary80,
        onSecondaryFixed = GraphiteSecondary10,
        onSecondaryFixedVariant = GraphiteSecondary30,
        tertiaryFixed = GraphiteTertiary90,
        tertiaryFixedDim = GraphiteTertiary80,
        onTertiaryFixed = GraphiteTertiary10,
        onTertiaryFixedVariant = GraphiteTertiary30,
    )

/**
 * Resolves the active [ColorScheme] for the current platform and user preferences.
 *
 * On Android 12+, returns a dynamic color scheme derived from the user's wallpaper when
 * [dynamicColor] is true. On all other platforms, or when dynamic color is unavailable, returns
 * [LightColorScheme] or [DarkColorScheme] based on [darkTheme].
 */
@Composable
expect fun rememberColorScheme(
    dynamicColor: Boolean,
    darkTheme: Boolean,
): ColorScheme

/**
 * A theme for the LogDate app.
 *
 * Uses Material 3 Expressive: components opt into expressive shape, motion, and color defaults via
 * the [MaterialExpressiveTheme] wrapper. Brand colors are preserved through [rememberColorScheme],
 * which still resolves to Material You dynamic colors on Android 12+ or to [LightColorScheme] /
 * [DarkColorScheme] elsewhere.
 *
 * @param dynamicColor Whether to use dynamic color scheme on Android 12+ devices.
 * @param darkTheme Whether the theme should be dark.
 * @param content The content of the theme.
 */
@Composable
fun LogDateTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val baseScheme = rememberColorScheme(dynamicColor, darkTheme)
    val platformSemantics = rememberPlatformSemanticColors(darkTheme)
    MaterialExpressiveTheme(
        colorScheme = baseScheme.applyPlatformSemantics(platformSemantics),
        typography = platformTypography(),
    ) {
        // MaterialTheme sets the colour scheme but not LocalContentColor, which stays at
        // Compose's default black. Any Text that does not name a colour therefore rendered
        // black-on-black in dark mode - screen titles across onboarding and account setup were
        // effectively invisible. A Surface at the root supplies both the themed background and
        // the matching content colour, so unstyled text is legible everywhere by default.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
