package app.logdate.feature.rewind.ui.detail

import androidx.compose.ui.graphics.Color
import app.logdate.shared.model.ActivityType
import app.logdate.shared.model.Rewind
import kotlin.math.abs

/**
 * The accent color a rewind tints its story chrome with.
 *
 * This is the visible identity of a rewind: progress bar, action icons, and any other
 * piece of chrome that wants a "this rewind is its own thing" hue. Two rewinds in the
 * same week of the year will not share a color, and a travel week never reads the same
 * as a focused-work week, so the chrome itself becomes a hint at what kind of week it
 * was before the user reads a single beat.
 *
 * Derivation:
 *  - The dominant [ActivityType] in [Rewind.metadata] picks a base hue from a small
 *    palette of mood-aligned colors. Travel reads warm and sandy, social reads bright,
 *    focused work reads cool and steady, etc.
 *  - The rewind's uid is hashed into a small offset that nudges the base hue's
 *    saturation/lightness so two rewinds with the same dominant activity still end up
 *    distinct from each other.
 *  - Rewinds with no metadata fall back to a hash-only palette index so a freshly
 *    generated rewind that hasn't been classified yet still gets a unique color
 *    instead of a default theme accent.
 *
 * The function is intentionally pure and synchronous so it can be called from a
 * composable without needing a remember-key beyond the rewind itself.
 */
fun Rewind.accentColor(): Color {
    val seed = uid.hashCode()
    val baseHue =
        when (metadata?.detectedActivities?.firstOrNull()) {
            ActivityType.TRAVEL -> TRAVEL_HUE
            ActivityType.SOCIAL -> SOCIAL_HUE
            ActivityType.FOCUSED_WORK -> FOCUSED_WORK_HUE
            ActivityType.QUIET -> QUIET_HUE
            ActivityType.MILESTONE -> MILESTONE_HUE
            ActivityType.MIXED -> MIXED_HUE
            null -> FALLBACK_PALETTE[abs(seed) % FALLBACK_PALETTE.size]
        }
    // Nudge the base hue along the L axis so two rewinds with the same dominant
    // activity still read as distinct from each other. The nudge is intentionally
    // small — it's identity, not contrast.
    val lightnessNudge = ((seed and 0x7) - 4) * 0.015f
    return baseHue.adjustLightness(lightnessNudge)
}

private fun Color.adjustLightness(delta: Float): Color {
    val nudged = (red + green + blue) / 3f + delta
    val factor = (nudged.coerceIn(0f, 1f) / ((red + green + blue) / 3f).coerceAtLeast(0.01f))
    return Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

// Mood-aligned base hues. Saturation kept moderate so the chrome reads as accent, not
// as a competing focal point against the panel content.
private val TRAVEL_HUE = Color(0xFFE8A86B) // warm sand
private val SOCIAL_HUE = Color(0xFFE57FB7) // bright magenta
private val FOCUSED_WORK_HUE = Color(0xFF6B9BD8) // cool steel
private val QUIET_HUE = Color(0xFF8FA888) // pine
private val MILESTONE_HUE = Color(0xFFE8C547) // honey gold
private val MIXED_HUE = Color(0xFFB388C9) // dusk lavender

private val FALLBACK_PALETTE =
    listOf(
        Color(0xFF7FB6A6), // sea
        Color(0xFFD18B7A), // clay
        Color(0xFF8B9DC3), // dust blue
        Color(0xFFC9B27A), // wheat
        Color(0xFFA28FB6), // soft purple
    )
