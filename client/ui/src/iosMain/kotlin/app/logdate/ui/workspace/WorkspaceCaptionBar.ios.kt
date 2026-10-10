package app.logdate.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun rememberPlatformCaptionBar(canvas: Color): WorkspaceCaptionBar? = null

internal actual fun Modifier.captionBarControl(): Modifier = this

/** Windows here have no Android caption bar to blend. */
@Suppress("ktlint:standard:function-naming")
@Composable
actual fun BlendedCaptionBar(background: Color) = Unit
