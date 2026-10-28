@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.logdate.ui.workspace.LocalWorkspacePlaybackInsets

@Composable
@Suppress("DEPRECATION")
internal actual fun RewindSystemBars() {
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = LocalWorkspacePlaybackInsets.current
    val top = insets.getTop(density)
    val bottom = insets.getBottom(density)
    val left = insets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val right = insets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    DisposableEffect(view, top, bottom, left, right) {
        val window = view.context.activity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowInsetsControllerCompat(window, view)
        val statusIcons = controller.isAppearanceLightStatusBars
        val navigationIcons = controller.isAppearanceLightNavigationBars
        val statusColor = window.statusBarColor
        val navigationColor = window.navigationBarColor
        val dividerColor = window.navigationBarDividerColor
        val statusContrast = if (Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced else false
        val navigationContrast = if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else false
        val touchesStatus = top > 0
        val navigationInsets = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.navigationBars())
        val navBottom = minOf(bottom, navigationInsets?.bottom ?: 0)
        val navLeft = minOf(left, navigationInsets?.left ?: 0)
        val navRight = minOf(right, navigationInsets?.right ?: 0)
        val touchesNavigation = navBottom > 0 || navLeft > 0 || navRight > 0
        if (touchesStatus) window.statusBarColor = Color.TRANSPARENT
        if (touchesNavigation) {
            window.navigationBarColor = Color.TRANSPARENT
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) {
            if (touchesStatus) window.isStatusBarContrastEnforced = false
            if (touchesNavigation) window.isNavigationBarContrastEnforced = false
        }
        var disposed = false
        var pending = false
        var lastSample = 0L
        val handler = Handler(Looper.getMainLooper())

        fun sample(
            rect: Rect,
            done: (List<Double>) -> Unit,
        ) {
            val bitmap = Bitmap.createBitmap(16, 2, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(window, rect, bitmap, { result ->
                    // Sample only this app surface; no System UI pixels or images are retained.
                    val luminances =
                        if (!disposed && result == PixelCopy.SUCCESS) {
                            (0 until bitmap.width).map { x -> ColorUtils.calculateLuminance(bitmap.getPixel(x, 1)) }
                        } else {
                            emptyList()
                        }
                    bitmap.recycle()
                    done(luminances)
                }, handler)
            } catch (_: IllegalArgumentException) {
                bitmap.recycle()
                done(emptyList())
            }
        }

        fun darkIcons(samples: List<Double>): Boolean = samples.sorted()[samples.size / 2] > 0.179
        val listener =
            ViewTreeObserver.OnDrawListener {
                val now = SystemClock.uptimeMillis()
                if (!disposed && !pending && view.width > 0 && view.height > 0) {
                    val delay = (200 - (now - lastSample)).coerceAtLeast(0L)
                    pending = true
                    view.postDelayed({
                        if (disposed) return@postDelayed
                        lastSample = SystemClock.uptimeMillis()
                        val iconRegion = with(density) { 72.dp.roundToPx() }.coerceAtMost(view.width / 2)
                        val statusLeft = Rect(0, 0, iconRegion, top.coerceIn(1, view.height))
                        val statusRight = Rect(view.width - iconRegion, 0, view.width, top.coerceIn(1, view.height))
                        val navigationRect =
                            when {
                                navBottom > 0 -> Rect(0, (view.height - navBottom).coerceAtLeast(0), view.width, view.height)
                                navLeft > 0 -> Rect(0, 0, navLeft.coerceAtMost(view.width), view.height)
                                else -> Rect((view.width - navRight).coerceAtLeast(0), 0, view.width, view.height)
                            }

                        fun navigationSample() {
                            if (!disposed && touchesNavigation) {
                                sample(navigationRect) { samples ->
                                    if (samples.isNotEmpty() && controller.isAppearanceLightNavigationBars != darkIcons(samples)) {
                                        controller.isAppearanceLightNavigationBars = darkIcons(samples)
                                    }
                                    pending = false
                                }
                            } else {
                                pending = false
                            }
                        }
                        if (touchesStatus) {
                            sample(statusLeft) { first ->
                                if (!disposed) {
                                    sample(statusRight) { second ->
                                        val samples = first + second
                                        if (!disposed &&
                                            samples.isNotEmpty() &&
                                            controller.isAppearanceLightStatusBars != darkIcons(samples)
                                        ) {
                                            controller.isAppearanceLightStatusBars = darkIcons(samples)
                                        }
                                        navigationSample()
                                    }
                                }
                            }
                        } else {
                            navigationSample()
                        }
                    }, delay)
                }
            }
        view.viewTreeObserver.addOnDrawListener(listener)
        onDispose {
            disposed = true
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
            if (touchesStatus) {
                controller.isAppearanceLightStatusBars = statusIcons
                window.statusBarColor = statusColor
            }
            if (touchesNavigation) {
                controller.isAppearanceLightNavigationBars = navigationIcons
                window.navigationBarColor = navigationColor
                window.navigationBarDividerColor = dividerColor
            }
            if (Build.VERSION.SDK_INT >= 29) {
                if (touchesStatus) window.isStatusBarContrastEnforced = statusContrast
                if (touchesNavigation) window.isNavigationBarContrastEnforced = navigationContrast
            }
        }
    }
}

private tailrec fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
