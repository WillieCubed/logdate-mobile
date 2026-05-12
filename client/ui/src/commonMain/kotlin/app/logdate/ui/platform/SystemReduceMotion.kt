package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Live observation of the OS "reduce motion / haptic feedback" setting. Returns `true` when
 * the user has globally turned haptic/touch feedback off (Android: Settings > Sound > Touch
 * feedback) or asked the system to reduce motion (iOS: Settings > Accessibility > Motion >
 * Reduce Motion). Pass the current value into [DefaultLogDateHaptics] so non-critical events
 * are suppressed automatically.
 *
 * Desktop returns a constant `false` — there is no equivalent system signal there.
 */
@Composable
expect fun rememberSystemReduceMotion(): State<Boolean>
