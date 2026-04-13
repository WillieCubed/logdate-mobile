package app.logdate.feature.journals.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.uuid.Uuid

/**
 * Derives a deterministic solid color from a journal's identity.
 *
 * Uses the journal ID's hash to pick a unique hue at a muted saturation
 * and medium lightness, so every journal gets a distinct but tasteful
 * color that stays stable across sessions and devices.
 *
 * This is the canonical journal-color function. UI surfaces (`JournalCover`)
 * and platform integrations (Android sharing-shortcut icons) both call this
 * directly so the visual identity stays consistent across the app.
 */
fun deriveCoverColor(journalId: Uuid): Color {
    val hue = abs(journalId.hashCode() % 360).toFloat()
    return Color.hsl(hue, saturation = 0.50f, lightness = 0.80f)
}
