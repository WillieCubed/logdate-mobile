package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformHapticsController(): PlatformHapticsController = remember { createPlatformHapticsController() }
