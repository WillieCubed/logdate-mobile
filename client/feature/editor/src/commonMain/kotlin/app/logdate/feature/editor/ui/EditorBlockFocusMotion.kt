@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.feature.editor.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.Dp
import kotlin.uuid.Uuid

internal val BlockFocusProgressKey = SemanticsPropertyKey<Float>("BlockFocusProgress")
internal val LocalBlockFocusId = compositionLocalOf<Uuid?> { null }

internal val LocalBlockFocusProgress = compositionLocalOf<Float?> { null }

internal fun <T> blockFocusSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)

@Composable
internal fun Modifier.editorBlockFocusElement(
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    blockId: Uuid,
    cornerRadius: Dp,
    focusProgress: Float,
): Modifier =
    with(sharedScope) {
        sharedElement(
            rememberSharedContentState("block_surface_$blockId"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ -> blockFocusSpring() },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cornerRadius * (1f - focusProgress))),
        )
    }
