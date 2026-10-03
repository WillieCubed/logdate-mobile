package app.logdate.wear.v2.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/**
 * The new watch app's colors when the system offers no dynamic color, which is every watch before
 * Wear OS 6.
 *
 * Every role but error is a neutral grey: the only warm color on the watch is the campfire's flame,
 * which the campfire draws itself. Text roles reach 4.5:1 on the surfaces they sit on and outlines
 * 3:1; `GraphiteColorSchemeTest` checks both.
 */
val GraphiteColorScheme: ColorScheme =
    ColorScheme(
        primary = Color(0xFFC9CED6),
        primaryDim = Color(0xFFAEB4BC),
        primaryContainer = Color(0xFF3A3F47),
        onPrimary = Color(0xFF1B1F26),
        onPrimaryContainer = Color(0xFFE1E5EC),
        secondary = Color(0xFFC4C7CC),
        secondaryDim = Color(0xFFA9ACB1),
        secondaryContainer = Color(0xFF3B3E43),
        onSecondary = Color(0xFF1D2024),
        onSecondaryContainer = Color(0xFFE0E3E8),
        tertiary = Color(0xFFD0CCC6),
        tertiaryDim = Color(0xFFB4B0AA),
        tertiaryContainer = Color(0xFF45423E),
        onTertiary = Color(0xFF211F1C),
        onTertiaryContainer = Color(0xFFECE8E2),
        surfaceContainerLow = Color(0xFF1B1C1E),
        surfaceContainer = Color(0xFF242528),
        surfaceContainerHigh = Color(0xFF2E3033),
        onSurface = Color(0xFFE4E5E8),
        onSurfaceVariant = Color(0xFFC2C5CA),
        outline = Color(0xFF8D9096),
        outlineVariant = Color(0xFF46484C),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        error = Color(0xFFFFB4AB),
        errorDim = Color(0xFFE89D94),
        errorContainer = Color(0xFF8C1D18),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
    )
