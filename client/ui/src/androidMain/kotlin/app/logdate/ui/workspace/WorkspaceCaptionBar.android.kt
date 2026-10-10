package app.logdate.ui.workspace

import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
internal actual fun rememberPlatformCaptionBar(canvas: Color): WorkspaceCaptionBar? {
    // Apps may draw in the caption bar from Android 15.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
    val view = LocalView.current
    val lightCanvas = canvas.luminance() > 0.5f
    DisposableEffect(view, lightCanvas) {
        val controller = view.windowInsetsController
        val transparent = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
        val lightControls = WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        controller?.setSystemBarsAppearance(transparent or (if (lightCanvas) lightControls else 0), transparent or lightControls)
        onDispose { controller?.setSystemBarsAppearance(0, transparent or lightControls) }
    }
    val density = LocalDensity.current
    val height = WindowInsets.captionBar.getTop(density)
    val windowWidth = LocalWindowInfo.current.containerSize.width
    return remember(view, height, windowWidth) {
        if (height == 0) return@remember null
        // The system's controls: the app menu at one edge and the window buttons at the other.
        val controls =
            ViewCompat
                .getRootWindowInsets(view)
                ?.getBoundingRects(WindowInsetsCompat.Type.captionBar())
                .orEmpty()
        val left = controls.filter { it.centerX() < windowWidth / 2 }.maxOfOrNull { it.right } ?: 0
        val right = controls.filter { it.centerX() >= windowWidth / 2 }.minOfOrNull { windowWidth - it.left } ?: 0
        with(density) { WorkspaceCaptionBar(height.toDp(), left.toDp(), right.toDp()) }
    }
}

internal actual fun Modifier.captionBarControl(): Modifier = systemGestureExclusion()
