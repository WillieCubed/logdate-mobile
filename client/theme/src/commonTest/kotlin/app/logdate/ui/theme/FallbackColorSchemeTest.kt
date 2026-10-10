package app.logdate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests the color schemes LogDate uses when dynamic color is unavailable: desktop, iOS, Android 11,
 * and anyone who turns dynamic color off.
 *
 * They are graphite: every role except the error colors is nearly gray, so the template purple and
 * pink cannot come back unnoticed, and every text color meets the WCAG AA minimum on its surface.
 */
class FallbackColorSchemeTest {
    @Test
    fun `the light fallback is graphite outside the error colors`() {
        assertNeutral("light", LightColorScheme)
    }

    @Test
    fun `the dark fallback is graphite outside the error colors`() {
        assertNeutral("dark", DarkColorScheme)
    }

    @Test
    fun `light fallback text is readable on its surface`() {
        assertReadable("light", LightColorScheme)
    }

    @Test
    fun `dark fallback text is readable on its surface`() {
        assertReadable("dark", DarkColorScheme)
    }

    @Test
    fun `the template purple is gone`() {
        assertNotEquals(Color(0xFF6650A4), LightColorScheme.primary)
        assertNotEquals(Color(0xFFD0BCFF), DarkColorScheme.primary)
    }

    private fun assertNeutral(
        name: String,
        scheme: ColorScheme,
    ) {
        val tinted = scheme.nonErrorRoles().filterValues { channelSpread(it) > MAX_CHANNEL_SPREAD }
        assertTrue(tinted.isEmpty(), "$name roles with a strong hue: ${tinted.keys}")
    }

    private fun assertReadable(
        name: String,
        scheme: ColorScheme,
    ) {
        val unreadable = scheme.textPairs().filterValues { (text, surface) -> contrast(text, surface) < MIN_CONTRAST }
        assertTrue(unreadable.isEmpty(), "$name pairs below the contrast minimum: ${unreadable.keys}")
    }

    private fun channelSpread(color: Color): Int {
        val channels = listOf(color.red, color.green, color.blue).map { (it * 255).roundToInt() }
        return channels.max() - channels.min()
    }

    private fun contrast(
        first: Color,
        second: Color,
    ): Double {
        val lighter = max(first.luminance(), second.luminance())
        val darker = min(first.luminance(), second.luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun ColorScheme.nonErrorRoles(): Map<String, Color> =
        mapOf(
            "primary" to primary,
            "onPrimary" to onPrimary,
            "primaryContainer" to primaryContainer,
            "onPrimaryContainer" to onPrimaryContainer,
            "inversePrimary" to inversePrimary,
            "secondary" to secondary,
            "onSecondary" to onSecondary,
            "secondaryContainer" to secondaryContainer,
            "onSecondaryContainer" to onSecondaryContainer,
            "tertiary" to tertiary,
            "onTertiary" to onTertiary,
            "tertiaryContainer" to tertiaryContainer,
            "onTertiaryContainer" to onTertiaryContainer,
            "background" to background,
            "onBackground" to onBackground,
            "surface" to surface,
            "onSurface" to onSurface,
            "surfaceVariant" to surfaceVariant,
            "onSurfaceVariant" to onSurfaceVariant,
            "surfaceTint" to surfaceTint,
            "inverseSurface" to inverseSurface,
            "inverseOnSurface" to inverseOnSurface,
            "outline" to outline,
            "outlineVariant" to outlineVariant,
            "scrim" to scrim,
            "surfaceBright" to surfaceBright,
            "surfaceDim" to surfaceDim,
            "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
            "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceContainerLow" to surfaceContainerLow,
            "surfaceContainerLowest" to surfaceContainerLowest,
            "primaryFixed" to primaryFixed,
            "primaryFixedDim" to primaryFixedDim,
            "onPrimaryFixed" to onPrimaryFixed,
            "onPrimaryFixedVariant" to onPrimaryFixedVariant,
            "secondaryFixed" to secondaryFixed,
            "secondaryFixedDim" to secondaryFixedDim,
            "onSecondaryFixed" to onSecondaryFixed,
            "onSecondaryFixedVariant" to onSecondaryFixedVariant,
            "tertiaryFixed" to tertiaryFixed,
            "tertiaryFixedDim" to tertiaryFixedDim,
            "onTertiaryFixed" to onTertiaryFixed,
            "onTertiaryFixedVariant" to onTertiaryFixedVariant,
        )

    private fun ColorScheme.textPairs(): Map<String, Pair<Color, Color>> =
        mapOf(
            "onPrimary on primary" to (onPrimary to primary),
            "onPrimaryContainer on primaryContainer" to (onPrimaryContainer to primaryContainer),
            "onSecondary on secondary" to (onSecondary to secondary),
            "onSecondaryContainer on secondaryContainer" to (onSecondaryContainer to secondaryContainer),
            "onTertiary on tertiary" to (onTertiary to tertiary),
            "onTertiaryContainer on tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
            "onBackground on background" to (onBackground to background),
            "onSurface on surface" to (onSurface to surface),
            "onSurfaceVariant on surfaceVariant" to (onSurfaceVariant to surfaceVariant),
            "inverseOnSurface on inverseSurface" to (inverseOnSurface to inverseSurface),
            "onSurface on surfaceContainerHighest" to (onSurface to surfaceContainerHighest),
            "onSurfaceVariant on surfaceContainer" to (onSurfaceVariant to surfaceContainer),
            "onPrimaryFixed on primaryFixed" to (onPrimaryFixed to primaryFixed),
            "onPrimaryFixedVariant on primaryFixed" to (onPrimaryFixedVariant to primaryFixed),
        )

    private companion object {
        /** The largest difference between two RGB channels, out of 255, that still reads as gray. */
        const val MAX_CHANNEL_SPREAD = 32

        /** WCAG 2.2 AA for body text. */
        const val MIN_CONTRAST = 4.5
    }
}
