package app.logdate.feature.rewind.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import java.awt.Dimension
import java.awt.Toolkit

/**
 * Desktop implementation of screen configuration.
 * This provides similar functionality to the Android LocalConfiguration,
 * but for desktop platforms.
 */
data class ScreenConfiguration(
    val screenWidthDp: Int,
    val screenHeightDp: Int
)

/**
 * CompositionLocal providing screen configuration for desktop platforms.
 */
val LocalConfiguration = staticCompositionLocalOf {
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    ScreenConfiguration(
        screenWidthDp = (screenSize.width / 1.5).toInt(),  // Approximate conversion to dp
        screenHeightDp = (screenSize.height / 1.5).toInt() // Approximate conversion to dp
    )
}

/**
 * Provides a screen configuration to desktop Compose UI.
 * This is a wrapper to provide similar functionality to Android's LocalConfiguration.
 */
@Composable
fun ProvideScreenConfiguration(content: @Composable () -> Unit) {
    val screenSize = Toolkit.getDefaultToolkit().screenSize
    val configuration = ScreenConfiguration(
        screenWidthDp = (screenSize.width / 1.5).toInt(),  // Approximate conversion to dp
        screenHeightDp = (screenSize.height / 1.5).toInt() // Approximate conversion to dp
    )
    
    CompositionLocalProvider(LocalConfiguration provides configuration) {
        content()
    }
}