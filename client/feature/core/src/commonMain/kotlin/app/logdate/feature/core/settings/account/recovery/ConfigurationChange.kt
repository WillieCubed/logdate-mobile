package app.logdate.feature.core.settings.account.recovery

import androidx.compose.runtime.Composable

/**
 * Returns a check for whether the screen is being torn down only to be rebuilt for a configuration
 * change, such as a rotation or a window resize, rather than because the person left it.
 */
@Composable
internal expect fun rememberIsChangingConfiguration(): () -> Boolean
