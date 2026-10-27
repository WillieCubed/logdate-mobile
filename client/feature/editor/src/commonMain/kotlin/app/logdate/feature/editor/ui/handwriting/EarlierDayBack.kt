package app.logdate.feature.editor.ui.handwriting

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.logdate.feature.editor.ui.blockFocusSpring
import app.logdate.ui.platform.PlatformPredictiveBackHandler
import app.logdate.ui.platform.rememberSystemReduceMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
internal fun rememberEarlierDayTransition(page: EntryPageOverviewState): androidx.compose.animation.core.Transition<Boolean> {
    val state = remember { SeekableTransitionState(page.earlierExpanded) }
    val scope = rememberCoroutineScope()
    val progress = remember { MutableStateFlow<Float?>(null) }
    val motion = remember { Animatable(if (page.earlierExpanded) 1f else 0f) }
    var gestureOrigin by remember { mutableFloatStateOf(1f) }
    val reduceMotion = rememberSystemReduceMotion().value
    LaunchedEffect(page.earlierExpanded) {
        if (state.targetState != page.earlierExpanded) state.animateTo(page.earlierExpanded)
    }
    LaunchedEffect(Unit) {
        progress.collectLatest { fraction ->
            if (fraction != null) state.seekTo(fraction, targetState = false)
        }
    }
    PlatformPredictiveBackHandler(
        enabled = page.earlierExpanded,
        onProgress = {
            if (progress.value == null) gestureOrigin = motion.value
            progress.value = if (reduceMotion) 0f else it
        },
        onBack = {
            scope.launch {
                progress.value = null
                state.animateTo(false)
                page.earlierExpanded = false
            }
        },
        onCancel = {
            scope.launch {
                progress.value = null
                state.animateTo(true)
            }
        },
    )
    val gesture by progress.collectAsState()
    LaunchedEffect(state.targetState, gesture) {
        val fraction = gesture
        if (fraction != null) {
            motion.snapTo(gestureOrigin * (1f - fraction))
        } else {
            motion.animateTo(if (state.targetState) 1f else 0f, blockFocusSpring())
        }
    }
    SideEffect { page.browsingProgress = motion.value }
    return rememberTransition(state, label = "earlierDay")
}
