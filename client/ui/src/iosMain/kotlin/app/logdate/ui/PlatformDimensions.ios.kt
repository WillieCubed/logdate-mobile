package app.logdate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import platform.UIKit.UIScreen
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectGetHeight

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual object PlatformDimensions {
    @Composable
    actual fun getScreenWidth(): Dp {
        // Use CGRectGetWidth to get the width instead of accessing the field directly
        val screenWidth = CGRectGetWidth(UIScreen.mainScreen.bounds)
        // Convert points to dp (approximate conversion)
        return (screenWidth / UIScreen.mainScreen.scale).dp
    }
    
    @Composable
    actual fun getScreenHeight(): Dp {
        // Use CGRectGetHeight to get the height instead of accessing the field directly
        val screenHeight = CGRectGetHeight(UIScreen.mainScreen.bounds)
        // Convert points to dp (approximate conversion)
        return (screenHeight / UIScreen.mainScreen.scale).dp
    }
}