package app.logdate.feature.editor.audio.color

import app.logdate.feature.editor.audio.model.AudioPalette
import app.logdate.feature.editor.audio.model.DaylightPeriod

/**
 * Generates contextual color palettes based on the time of day.
 *
 * Each palette is designed to evoke the mood and lighting conditions
 * of the time when the audio was recorded, creating a visual identity
 * that connects the memory to its temporal context.
 */
class PaletteGenerator {

    /**
     * Generates a color palette for the given daylight period.
     */
    fun generate(daylightPeriod: DaylightPeriod): AudioPalette {
        return getBasePalette(daylightPeriod)
    }

    private fun getBasePalette(period: DaylightPeriod): AudioPalette = when (period) {
        DaylightPeriod.DAWN -> AudioPalette(
            waveformGradientStart = 0xFF1A1A2E,   // Deep navy
            waveformGradientEnd = 0xFFFF9F7F,     // Soft coral
            playedFillColor = 0xFFFFB366,         // Warm orange
            accentColor = 0xFFFF7F50,             // Coral
            immersiveBackground = 0xFF0D0D1A     // Near black blue
        )

        DaylightPeriod.MORNING -> AudioPalette(
            waveformGradientStart = 0xFF2D2D44,   // Muted purple-gray
            waveformGradientEnd = 0xFFFFD699,     // Soft gold
            playedFillColor = 0xFFFFB84D,         // Golden yellow
            accentColor = 0xFFFF9F40,             // Orange gold
            immersiveBackground = 0xFF1A1A2E     // Deep navy
        )

        DaylightPeriod.MIDDAY -> AudioPalette(
            waveformGradientStart = 0xFF3D5A80,   // Steel blue
            waveformGradientEnd = 0xFFF5F5F5,     // Near white
            playedFillColor = 0xFF5C8DC7,         // Sky blue
            accentColor = 0xFF2E86AB,             // Ocean blue
            immersiveBackground = 0xFF1A2A40     // Dark blue
        )

        DaylightPeriod.AFTERNOON -> AudioPalette(
            waveformGradientStart = 0xFF4A5568,   // Slate gray
            waveformGradientEnd = 0xFFFFE4B5,     // Moccasin
            playedFillColor = 0xFFDEB887,         // Burlywood
            accentColor = 0xFFCD853F,             // Peru
            immersiveBackground = 0xFF2D2D2D     // Dark gray
        )

        DaylightPeriod.GOLDEN_HOUR -> AudioPalette(
            waveformGradientStart = 0xFF4A3728,   // Deep brown
            waveformGradientEnd = 0xFFFFD700,     // Gold
            playedFillColor = 0xFFFFA500,         // Orange
            accentColor = 0xFFFF8C00,             // Dark orange
            immersiveBackground = 0xFF1A1208     // Deep warm brown
        )

        DaylightPeriod.EVENING -> AudioPalette(
            waveformGradientStart = 0xFF1A1A2E,   // Deep navy
            waveformGradientEnd = 0xFF9B59B6,     // Amethyst
            playedFillColor = 0xFF8E44AD,         // Purple
            accentColor = 0xFF6C3483,             // Dark purple
            immersiveBackground = 0xFF0D0D1A     // Near black
        )

        DaylightPeriod.NIGHT -> AudioPalette(
            waveformGradientStart = 0xFF0D0D1A,   // Near black
            waveformGradientEnd = 0xFF2C3E50,     // Midnight blue
            playedFillColor = 0xFF34495E,         // Wet asphalt
            accentColor = 0xFF5DADE2,             // Light blue accent
            immersiveBackground = 0xFF050508     // Pure dark
        )
    }
}
