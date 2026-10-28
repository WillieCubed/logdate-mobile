@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.blockFocusSpring
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.memory_add
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun rememberCreationProgress(
    visible: Boolean,
    reduceMotion: Boolean,
): Float {
    val progress = remember { Animatable(if (reduceMotion && visible) 1f else 0f) }
    LaunchedEffect(visible, reduceMotion) {
        val target = if (visible) 1f else 0f
        if (reduceMotion) progress.snapTo(target) else progress.animateTo(target, blockFocusSpring())
    }
    return progress.value
}

/** One persistent creation surface, outside the input viewport; it never edits an existing memory. */
@Composable
internal fun EntryCreationDock(
    mode: StoryCaptureMode,
    onMode: (StoryCaptureMode) -> Unit,
    onAddMemory: () -> Unit,
    modeEligible: Boolean,
    addEligible: Boolean,
    modeProgress: Float,
    addProgress: Float,
    chromeHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val progress = maxOf(modeProgress, addProgress)
    Surface(
        modifier.testTag("entry_creation_actions").clipToBounds(),
        shape = RoundedCornerShape(8.dp + 20.dp * progress),
        color = lerp(MaterialTheme.colorScheme.surfaceDim, MaterialTheme.colorScheme.surfaceContainerHigh, progress),
        tonalElevation = 1.dp,
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(chromeHeight * modeProgress)
                    .clipToBounds()
                    .graphicsLayer { alpha = if (progress > 0f) modeProgress / progress else 0f }
                    .semantics { if (!modeEligible) hideFromAccessibility() }
                    .testTag("entry_capture_tools")
                    .padding(horizontal = 8.dp, vertical = if (chromeHeight >= 64.dp) 8.dp else 0.dp),
            ) {
                StoriesCaptureModes(mode, onMode, compact = true, horizontalInset = 0.dp, enabled = modeEligible && modeProgress >= 0.99f)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(chromeHeight * addProgress)
                    .clipToBounds()
                    .graphicsLayer { alpha = if (progress > 0f) addProgress / progress else 0f }
                    .semantics { if (!addEligible) hideFromAccessibility() }
                    .testTag("entry_add_memory")
                    .padding(horizontal = 8.dp, vertical = if (chromeHeight >= 64.dp) 8.dp else 0.dp),
            ) {
                TextButton(onClick = onAddMemory, enabled = addEligible && addProgress >= 0.99f, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.memory_add))
                }
            }
        }
    }
}
