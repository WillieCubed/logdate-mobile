package app.logdate.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun rememberPlatformCaptionBar(canvas: Color): WorkspaceCaptionBar? = null

internal actual fun Modifier.captionBarControl(): Modifier = this
