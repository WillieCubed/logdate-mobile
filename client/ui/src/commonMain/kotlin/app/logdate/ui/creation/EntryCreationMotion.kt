@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.logdate.ui.creation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.logdate.ui.LocalNavAnimatedVisibilityScope
import app.logdate.ui.LocalSharedTransitionScope
import app.logdate.ui.platform.rememberSystemReduceMotion

val LocalEntryCreationProgress = compositionLocalOf { 1f }

/** The existing Home/editor bounds timing, also used by route-linked foreground reveal. */
fun <T> entryCreationMotionSpec(reduceMotion: Boolean): FiniteAnimationSpec<T> =
    if (reduceMotion) snap() else tween(350, easing = FastOutSlowInEasing)

fun entryCreationBarProgress(routeProgress: Float): Float = ((routeProgress - 0.55f) / 0.45f).coerceIn(0f, 1f)

@Composable
fun rememberEntryCreationVisibilityProgress(): Float {
    val scope = LocalNavAnimatedVisibilityScope.current ?: return 1f
    val reduceMotion = rememberSystemReduceMotion().value
    val progress by scope.transition.animateFloat(
        transitionSpec = { entryCreationMotionSpec(reduceMotion) },
        label = "entryCreationRouteVisibility",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return progress.coerceIn(0f, 1f)
}

@Composable
internal fun entryCreationBaseShape(kind: EntryCreationShape): Shape =
    when (kind) {
        EntryCreationShape.Round -> IconButtonDefaults.filledShape
        EntryCreationShape.Large -> MaterialTheme.shapes.large
    }

/** Both endpoints capture the navigation scope; inner editor scopes keep their own block overlays. */
@Composable
fun Modifier.entryCreationBounds(
    sourceId: String?,
    destination: Boolean = false,
    sourceShape: EntryCreationShape = EntryCreationShape.Large,
): Modifier {
    if (sourceId == null) return this
    val launcher = LocalEntryCreationLauncher.current
    if (!destination && launcher != null) {
        val owner = remember(launcher, sourceId) { Any() }
        DisposableEffect(launcher, sourceId, owner) {
            onDispose { launcher.unregisterSourceShape(sourceId, owner) }
        }
        SideEffect { launcher.registerSourceShape(sourceId, owner, sourceShape) }
    }
    val resolvedShape = launcher?.sourceShape(sourceId, sourceShape) ?: sourceShape
    val scope = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val progress = rememberEntryCreationVisibilityProgress()
    val reduceMotion = rememberSystemReduceMotion().value
    val flatten = if (destination) progress else 1f - progress
    val shape = EntryCreationOverlayShape(entryCreationBaseShape(resolvedShape), flatten)
    return with(scope) {
        this@entryCreationBounds.sharedBounds(
            rememberSharedContentState(entryCreationKey(sourceId)),
            visibility,
            boundsTransform = BoundsTransform { _, _ -> entryCreationMotionSpec(reduceMotion) },
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

/** Preserve theme corner geometry and reduce every radius to zero on the same route clock. */
private class EntryCreationOverlayShape(
    private val base: Shape,
    private val flatten: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val outline = base.createOutline(size, layoutDirection, density)
        if (flatten >= 1f) {
            return Outline.Rectangle(
                androidx.compose.ui.geometry
                    .Rect(0f, 0f, size.width, size.height),
            )
        }
        if (outline !is Outline.Rounded) return outline
        val scale = 1f - flatten.coerceIn(0f, 1f)

        fun CornerRadius.scaled() = CornerRadius(x * scale, y * scale)
        val rounded = outline.roundRect
        return Outline.Rounded(
            RoundRect(
                rounded.left,
                rounded.top,
                rounded.right,
                rounded.bottom,
                rounded.topLeftCornerRadius.scaled(),
                rounded.topRightCornerRadius.scaled(),
                rounded.bottomRightCornerRadius.scaled(),
                rounded.bottomLeftCornerRadius.scaled(),
            ),
        )
    }
}
