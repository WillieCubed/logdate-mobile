package app.logdate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Platform-specific dimensions provider that works across all platforms.
 * This abstraction is needed since LocalConfiguration is not available on all platforms.
 */
expect object PlatformDimensions {
    /**
     * Gets the current screen width in dp
     */
    @Composable
    fun getScreenWidth(): Dp
    
    /**
     * Gets the current screen height in dp
     */
    @Composable
    fun getScreenHeight(): Dp
}