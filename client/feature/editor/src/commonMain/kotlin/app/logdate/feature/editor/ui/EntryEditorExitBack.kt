package app.logdate.feature.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.PlatformPredictiveBackHandler
import app.logdate.ui.platform.rememberSystemReduceMotion
import kotlinx.coroutines.launch

/** A cancelled exit never opens confirmation or changes the draft. Clean exits belong to navigation. */
@Composable
internal fun rememberEntryEditorExitBack(
    enabled: Boolean,
    onCommit: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val reduceMotion = rememberSystemReduceMotion().value
    PlatformPredictiveBackHandler(
        enabled = enabled,
        onProgress = { fraction -> scope.launch { progress.snapTo(fraction) } },
        onBack = {
            onCommit()
            scope.launch { progress.animateTo(0f, blockFocusSpring()) }
        },
        onCancel = { scope.launch { progress.animateTo(0f, blockFocusSpring()) } },
    )
    return Modifier.graphicsLayer {
        val fraction = if (reduceMotion) 0f else progress.value
        scaleX = 1f - 0.04f * fraction
        scaleY = scaleX
        shape = RoundedCornerShape(24.dp * fraction)
        clip = fraction > 0f
    }
}
