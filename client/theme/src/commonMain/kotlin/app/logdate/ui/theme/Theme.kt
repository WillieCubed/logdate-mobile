@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The default light color scheme for the LogDate app.
 *
 * This should be used when dynamic color scheme is not available on Android 12+ devices.
 */
val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
    )

/**
 * The default dark color scheme for the LogDate app.
 *
 * This should be used when dynamic color scheme is not available on Android 12+ devices.
 */
val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
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
 * The LogDate theme uses Material You's dynamic color scheme on Android 12+ devices. When dynamic
 * color is not available, a default dark or light color scheme is used based on the system's dark
 * theme setting.
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
    MaterialTheme(
        colorScheme = rememberColorScheme(dynamicColor, darkTheme),
        typography = Typography,
        content = content,
    )
}
