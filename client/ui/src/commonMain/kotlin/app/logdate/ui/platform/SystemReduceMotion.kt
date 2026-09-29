package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Observation of the OS motion preference. Android reads the animator duration scale
 * (zero when system animations are disabled) on resume. iOS observes the accessibility
 * Reduce Motion setting. Pass the current value into [DefaultLogDateHaptics] so
 * non-critical events are suppressed automatically.
 *
 * Desktop returns a constant `false` — there is no equivalent system signal there.
 */
@Composable
expect fun rememberSystemReduceMotion(): State<Boolean>

internal fun isMotionReducedByAnimatorScale(scale: Float): Boolean = scale <= 0f
