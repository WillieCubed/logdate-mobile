@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.LocalBlockFocusId
import app.logdate.feature.editor.ui.LocalBlockFocusProgress
import app.logdate.feature.editor.ui.LocalSharedTransitionScope
import kotlin.uuid.Uuid

@Composable
internal fun InlineHandwritingBlock(
    blockId: Uuid,
    page: HandwritingPage,
    tools: HandwritingDrawingSettings,
    focusProgress: Float,
    description: String,
    paperColor: Color = Color.White,
    fitPreviewToWidth: Boolean = false,
    minPreviewHeight: Dp = 0.dp,
    adaptInkToPaper: Boolean = false,
    shareCanvas: Boolean = true,
    onFocus: () -> Unit,
) {
    val sharedProgress = LocalBlockFocusProgress.current
    val progress =
        if (sharedProgress == null) {
            focusProgress
        } else if (LocalBlockFocusId.current == blockId) {
            sharedProgress
        } else {
            0f
        }
    val morphing = LocalSharedTransitionScope.current?.isTransitionActive == true
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints((if (fitPreviewToWidth) Modifier.widthIn(max = 360.dp) else Modifier).fillMaxWidth()) {
        val bottom = page.strokes.maxOfOrNull { stroke -> stroke.points.maxOfOrNull { it.y } ?: 0f } ?: 96f
        val height = (if (fitPreviewToWidth) maxWidth * ((bottom + 48f) / page.width) else 240.dp).coerceAtLeast(minPreviewHeight)
        HandwritingCanvas(
            page,
            Modifier
                .fillMaxWidth()
                .then(if (shareCanvas) Modifier.handwritingSharedElement(blockId, progress) else Modifier)
                .height(height)
                .then(
                    if (shareCanvas) {
                        Modifier.clickable(enabled = !morphing, onClick = onFocus)
                    } else {
                        Modifier.clickable(interactionSource = interaction, indication = null, enabled = !morphing, onClick = onFocus)
                    },
                ),
            presentationProgress = progress,
            ruled = if (fitPreviewToWidth) false else tools.ruled,
            description = description,
            paperColor = paperColor,
            fitPreviewToWidth = fitPreviewToWidth,
            adaptInkToPaper = adaptInkToPaper,
        )
    }
}
