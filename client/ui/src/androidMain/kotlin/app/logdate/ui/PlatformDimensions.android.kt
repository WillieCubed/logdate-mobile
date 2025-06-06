package app.logdate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

actual object PlatformDimensions {
    @Composable
    actual fun getScreenWidth(): Dp {
        val configuration = LocalConfiguration.current
        return configuration.screenWidthDp.dp
    }
    
    @Composable
    actual fun getScreenHeight(): Dp {
        val configuration = LocalConfiguration.current
        return configuration.screenHeightDp.dp
    }
}