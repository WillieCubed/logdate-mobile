package app.logdate.wear.v2.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import io.mockk.mockk
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Checks the graphite fallback against WCAG 2.2: 4.5:1 for text and icons on the surface they sit
 * on, 3:1 for outlines and other non-text boundaries. Also checks the scheme stays neutral, so the
 * campfire's flame is the only warm color on the watch.
 */
class GraphiteColorSchemeTest {
    private val scheme = GraphiteColorScheme
    private val surfaces =
        mapOf(
            "background" to scheme.background,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        )

    @Test
    fun `text on filled roles reaches 4_5 to 1`() {
        val pairs =
            mapOf(
                "onPrimary on primary" to (scheme.onPrimary to scheme.primary),
                "onPrimary on primaryDim" to (scheme.onPrimary to scheme.primaryDim),
                "onPrimaryContainer on primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onSecondary on secondary" to (scheme.onSecondary to scheme.secondary),
                "onSecondary on secondaryDim" to (scheme.onSecondary to scheme.secondaryDim),
                "onSecondaryContainer on secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "onTertiary on tertiary" to (scheme.onTertiary to scheme.tertiary),
                "onTertiary on tertiaryDim" to (scheme.onTertiary to scheme.tertiaryDim),
                "onTertiaryContainer on tertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "onError on error" to (scheme.onError to scheme.error),
                "onError on errorDim" to (scheme.onError to scheme.errorDim),
                "onErrorContainer on errorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                "onBackground on background" to (scheme.onBackground to scheme.background),
            )

        pairs.forEach { (name, colors) -> assertContrast(name, colors.first, colors.second, TEXT_MINIMUM) }
    }

    @Test
    fun `text and accents on every surface reach 4_5 to 1`() {
        surfaces.forEach { (surfaceName, surface) ->
            assertContrast("onSurface on $surfaceName", scheme.onSurface, surface, TEXT_MINIMUM)
            assertContrast("onSurfaceVariant on $surfaceName", scheme.onSurfaceVariant, surface, TEXT_MINIMUM)
            assertContrast("primary on $surfaceName", scheme.primary, surface, TEXT_MINIMUM)
        }
    }

    @Test
    fun `outlines reach 3 to 1 on every surface`() {
        surfaces.forEach { (surfaceName, surface) ->
            assertContrast("outline on $surfaceName", scheme.outline, surface, OUTLINE_MINIMUM)
        }
    }

    @Test
    fun `every role except error is a neutral grey`() {
        val neutralRoles =
            mapOf(
                "primary" to scheme.primary,
                "primaryDim" to scheme.primaryDim,
                "primaryContainer" to scheme.primaryContainer,
                "onPrimary" to scheme.onPrimary,
                "onPrimaryContainer" to scheme.onPrimaryContainer,
                "secondary" to scheme.secondary,
                "secondaryDim" to scheme.secondaryDim,
                "secondaryContainer" to scheme.secondaryContainer,
                "onSecondary" to scheme.onSecondary,
                "onSecondaryContainer" to scheme.onSecondaryContainer,
                "tertiary" to scheme.tertiary,
                "tertiaryDim" to scheme.tertiaryDim,
                "tertiaryContainer" to scheme.tertiaryContainer,
                "onTertiary" to scheme.onTertiary,
                "onTertiaryContainer" to scheme.onTertiaryContainer,
                "outline" to scheme.outline,
                "outlineVariant" to scheme.outlineVariant,
                "onSurface" to scheme.onSurface,
                "onSurfaceVariant" to scheme.onSurfaceVariant,
                "onBackground" to scheme.onBackground,
            ) + surfaces

        neutralRoles.forEach { (name, color) ->
            val chroma = maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue)
            assertTrue(chroma <= MAX_NEUTRAL_CHROMA, "$name has chroma $chroma")
        }
    }

    @Test
    fun `turning dynamic color off always gives graphite`() {
        assertSame(GraphiteColorScheme, colorSchemeFor(mockk<Context>(), dynamicColor = false))
    }

    private fun assertContrast(
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(ratio >= minimum, "$name is ${"%.2f".format(ratio)}:1, needs $minimum:1")
    }

    private fun contrastRatio(
        first: Color,
        second: Color,
    ): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        const val TEXT_MINIMUM = 4.5
        const val OUTLINE_MINIMUM = 3.0
        const val MAX_NEUTRAL_CHROMA = 0.06f
    }
}
