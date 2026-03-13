package app.logdate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Resolves the active [ColorScheme] based on user preferences and platform capabilities.
 *
 * iOS does not support dynamic color. Always returns [DarkColorScheme] or [LightColorScheme]
 * based on [darkTheme]; [dynamicColor] is ignored.
 *
 * @param dynamicColor Unused on iOS.
 * @param darkTheme Whether to use the dark variant of the resolved color scheme.
 */
@Composable
actual fun rememberColorScheme(
    dynamicColor: Boolean,
    darkTheme: Boolean,
): ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
