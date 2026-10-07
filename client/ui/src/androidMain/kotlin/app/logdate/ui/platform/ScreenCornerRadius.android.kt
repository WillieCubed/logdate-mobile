package app.logdate.ui.platform

import android.os.Build
import android.view.RoundedCorner
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp

@Suppress("ktlint:standard:function-naming")
@Composable
actual fun rememberScreenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current

    fun readRadius(): Dp {
        val radiusPx =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.rootWindowInsets
                    ?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                    ?.radius
            } else {
                null
            }
        return radiusPx?.let { with(density) { it.toDp() } } ?: DefaultScreenCornerRadius
    }
    var radius by remember(view, density) { mutableStateOf(readRadius()) }
    DisposableEffect(view, density) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener { radius = readRadius() }
        observer.addOnGlobalLayoutListener(listener)
        radius = readRadius()
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }
    return radius
}
