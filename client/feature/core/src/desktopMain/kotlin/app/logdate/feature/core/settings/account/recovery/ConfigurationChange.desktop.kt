package app.logdate.feature.core.settings.account.recovery

import androidx.compose.runtime.Composable

/** The window is resized in place here; the screen is never rebuilt for it. */
@Composable
internal actual fun rememberIsChangingConfiguration(): () -> Boolean = { false }
