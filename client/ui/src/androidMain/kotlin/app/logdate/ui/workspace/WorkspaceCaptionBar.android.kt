package app.logdate.ui.workspace

import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

private val transparentCaptionRequests = CaptionRequests<View>()

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun BlendedCaptionBar(background: Color) {
    // Apps may draw in the caption bar from Android 15.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val view = LocalView.current
    val lightCanvas = background.luminance() > 0.5f
    DisposableEffect(view, lightCanvas) {
        val controller = view.windowInsetsController
        val transparent = WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
        val lightControls = WindowInsetsController.APPEARANCE_LIGHT_CAPTION_BARS
        val window = view.rootView
        transparentCaptionRequests.acquire(window)
        controller?.setSystemBarsAppearance(transparent or (if (lightCanvas) lightControls else 0), transparent or lightControls)
        onDispose {
            if (transparentCaptionRequests.release(window)) controller?.setSystemBarsAppearance(0, transparent or lightControls)
        }
    }
}

@Composable
internal actual fun rememberPlatformCaptionBar(canvas: Color): WorkspaceCaptionBar? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
    BlendedCaptionBar(canvas)
    val view = LocalView.current
    val density = LocalDensity.current
    // Read after every layout of the window: resizing changes the window's width and the controls'
    // rectangles in separate passes, and neither alone is reliable.
    var captionBar by remember(view) { mutableStateOf(readCaptionBar(view, density)) }
    DisposableEffect(view, density) {
        val update = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> captionBar = readCaptionBar(view, density) }
        view.addOnLayoutChangeListener(update)
        captionBar = readCaptionBar(view, density)
        onDispose { view.removeOnLayoutChangeListener(update) }
    }
    // The caption bar appearing or going away changes the window's insets.
    val captionHeight = WindowInsets.captionBar.getTop(density)
    LaunchedEffect(captionHeight) { captionBar = readCaptionBar(view, density) }
    return captionBar
}

private fun readCaptionBar(
    view: View,
    density: Density,
): WorkspaceCaptionBar? {
    val insets = ViewCompat.getRootWindowInsets(view) ?: return null
    val height = insets.getInsets(WindowInsetsCompat.Type.captionBar()).top
    if (height == 0) return null
    // The system's controls: the app menu at one edge and the window buttons at the other.
    val controls =
        insets
            .getBoundingRects(WindowInsetsCompat.Type.captionBar())
            .map { IntRect(it.left, it.top, it.right, it.bottom) }
    val (left, right) = captionControlInsets(controls, view.width)
    return with(density) { WorkspaceCaptionBar(height.toDp(), left.toDp(), right.toDp()) }
}

internal actual fun Modifier.captionBarControl(): Modifier = systemGestureExclusion()
