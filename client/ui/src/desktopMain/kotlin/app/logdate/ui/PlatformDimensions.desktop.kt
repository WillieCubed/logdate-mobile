package app.logdate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Toolkit

actual object PlatformDimensions {
    @Composable
    actual fun getScreenWidth(): Dp {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val density = LocalDensity.current.density
        return (screenSize.width / density).dp
    }
    
    @Composable
    actual fun getScreenHeight(): Dp {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val density = LocalDensity.current.density
        return (screenSize.height / density).dp
    }
}