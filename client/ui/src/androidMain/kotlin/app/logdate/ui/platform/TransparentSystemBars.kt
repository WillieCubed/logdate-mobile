package app.logdate.ui.platform

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Keep scene paint continuous through bars without replaying Activity's automatic contrast style. */
fun ComponentActivity.enableTransparentSystemBars() {
    WindowCompat.enableEdgeToEdge(window)
    val darkIcons = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = darkIcons
        isAppearanceLightNavigationBars = darkIcons
    }
}
