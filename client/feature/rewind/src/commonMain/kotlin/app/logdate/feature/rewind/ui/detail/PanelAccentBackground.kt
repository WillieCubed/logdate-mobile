package app.logdate.feature.rewind.ui.detail

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Picks a deep, quiet background color for an AI-generated panel from a small palette.
 *
 * Quote panels and noticing-prompt panels both want a per-card hue so a stretch of them
 * doesn't read as templated, but the palette intentionally avoids bright accents — the
 * user's words are supposed to be the loudest thing on the panel, not the chrome.
 *
 * @param seed any int derived from rewind/panel identity; the same seed always picks the
 *   same color, distinct seeds vary the color across consecutive panels.
 */
internal fun panelAccentBackground(seed: Int): Color = ACCENT_PALETTE[abs(seed) % ACCENT_PALETTE.size]

private val ACCENT_PALETTE =
    listOf(
        Color(0xFF14202E), // ink blue
        Color(0xFF231828), // plum
        Color(0xFF1B2A1F), // pine
        Color(0xFF2E1B1B), // cocoa
        Color(0xFF1F1F26), // charcoal
    )
