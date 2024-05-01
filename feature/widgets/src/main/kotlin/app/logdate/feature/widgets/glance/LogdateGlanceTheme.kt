package app.logdate.feature.widgets.glance

import androidx.compose.ui.unit.sp
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import app.logdate.ui.theme.DarkColorScheme
import app.logdate.ui.theme.LightColorScheme

/**
 * A theme for LogDate Glance widgets.
 */
internal object LogdateGlanceTheme {
    /**
     * The LogDate color scheme for Glance widgets.
     */
    internal val colors = ColorProviders(
        light = LightColorScheme,
        dark = DarkColorScheme,
    )

    internal val typography = LogdateTypography
}

internal object LogdateTypography {
    val displayLarge = TextStyle(
        fontSize = 57.sp,
        fontWeight = FontWeight.Medium,
    )
    val displayMedium = TextStyle(
        fontSize = 45.sp,
        fontWeight = FontWeight.Medium,
    )
    val displaySmall = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Medium,
    )
    val headlineLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
    )
    val headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Medium,
    )
    val headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium,
    )
    val titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
    )
    val titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    )
    val titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
    val labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    )
    val labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    )
    val labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
    )
    val bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    )
    val bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
    val bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
}


internal val GlanceTheme.typography
    get() = LogdateGlanceTheme.typography