@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.rewind.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import app.logdate.ui.workspace.LocalWorkspacePlaybackInsets

@Composable
internal actual fun RewindSystemBars() {
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = LocalWorkspacePlaybackInsets.current
    val touchesStatus = insets.getTop(density) > 0
    val touchesNavigation = insets.getBottom(density) > 0
    DisposableEffect(view, touchesStatus, touchesNavigation) {
        val window = view.context.activity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowInsetsControllerCompat(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        if (touchesStatus) controller.isAppearanceLightStatusBars = false
        if (touchesNavigation) controller.isAppearanceLightNavigationBars = false
        onDispose {
            if (touchesStatus) controller.isAppearanceLightStatusBars = status
            if (touchesNavigation) controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

private tailrec fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }
