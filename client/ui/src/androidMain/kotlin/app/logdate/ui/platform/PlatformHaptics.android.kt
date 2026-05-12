package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual fun createPlatformHapticsController(): PlatformHapticsController = NoOpPlatformHaptics

@Composable
actual fun rememberPlatformHapticsController(): PlatformHapticsController {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidPlatformHaptics(context) }
}
