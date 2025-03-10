package app.logdate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The default light color scheme for the LogDate app.
 *
 * This should be used when dynamic color scheme is not available on Android 12+ devices.
 */
val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

/**
 * The default dark color scheme for the LogDate app.
 *
 * This should be used when dynamic color scheme is not available on Android 12+ devices.
 */
val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

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
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // TODO: Re-enable dynamic color when able
//        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//            val context = LocalContext.current
//            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
//    val view = LocalView.current

//    @Suppress("DEPRECATION")
//    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
//        if (!view.isInEditMode) {
//            SideEffect {
//                val window = (view.context as Activity).window
//                window.statusBarColor = Color.TRANSPARENT
//                window.navigationBarColor = Color.TRANSPARENT
//            }
//        }
//    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}