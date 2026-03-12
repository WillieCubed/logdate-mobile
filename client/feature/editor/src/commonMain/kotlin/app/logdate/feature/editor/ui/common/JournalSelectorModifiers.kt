@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.common

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Number of journal items visible before the list scrolls
private const val JOURNAL_PICKER_VISIBLE_ITEMS = 4

// MD3 DropdownMenuItem minimum touch target (48dp) + our external vertical padding (2dp × 2)
private val JOURNAL_PICKER_ITEM_HEIGHT: Dp = 52.dp

// "Journals" label row height
private val JOURNAL_PICKER_HEADER_HEIGHT: Dp = 36.dp

// Reserved for system bars, editor toolbar, collapsed selector card, and breathing room.
// Sized conservatively so the list stays comfortably on screen even in landscape.
private val JOURNAL_PICKER_SCREEN_MARGIN: Dp = 160.dp

internal val JournalSelectorShape = RoundedCornerShape(16.dp)

// MD3 Expressive emphasized easing curves per the motion spec
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Enter transition for the journal picker expanding upward from its anchor.
 */
internal fun journalSelectorEnterTransition(): EnterTransition =
    expandVertically(
        animationSpec = tween(400, easing = EmphasizedDecelerate),
        expandFrom = Alignment.Bottom,
    ) + fadeIn(tween(200, delayMillis = 80))

/**
 * Exit transition for the journal picker collapsing back down to its anchor.
 */
internal fun journalSelectorExitTransition(): ExitTransition =
    shrinkVertically(
        animationSpec = tween(350, easing = EmphasizedAccelerate),
        shrinkTowards = Alignment.Bottom,
    ) + fadeOut(tween(200))

/**
 * Constrains the journal selector height when expanded.
 *
 * The natural height shows [JOURNAL_PICKER_VISIBLE_ITEMS] items. This is capped against the
 * current window height minus [JOURNAL_PICKER_SCREEN_MARGIN] so the list never overflows on
 * compact screens (e.g. landscape phones where height can be as low as ~360dp).
 */
@Composable
fun Modifier.journalSelectorExpandedHeight(): Modifier {
    val naturalHeight =
        JOURNAL_PICKER_ITEM_HEIGHT * JOURNAL_PICKER_VISIBLE_ITEMS + JOURNAL_PICKER_HEADER_HEIGHT
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val maxHeight = minOf(naturalHeight, windowHeight - JOURNAL_PICKER_SCREEN_MARGIN)
    return heightIn(max = maxHeight)
}
