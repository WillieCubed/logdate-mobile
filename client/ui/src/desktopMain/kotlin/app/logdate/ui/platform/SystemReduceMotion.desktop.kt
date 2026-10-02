package app.logdate.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberSystemReduceMotion(): State<Boolean> {
    LocalReduceMotionOverride.current?.let { return rememberUpdatedState(it) }
    return remember { mutableStateOf(false) }
}
