package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fallback corner radius used wherever the platform can't report the display's actual
 * physical corner radius: iOS has no public API for this, Desktop windows aren't
 * physically rounded, and Android below API 31 doesn't expose [android.view.RoundedCorner].
 */
val DefaultScreenCornerRadius: Dp = 28.dp

/**
 * The physical display's corner radius, so UI that insets from the screen edge can round
 * its own corners concentrically with the device instead of picking an arbitrary radius.
 * Falls back to [DefaultScreenCornerRadius] wherever the platform can't report a real value.
 */
@Composable
expect fun rememberScreenCornerRadius(): Dp
