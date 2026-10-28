package app.logdate.ui.workspace

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class SupportingExtent { Peek, Browsing, Expanded, Focused }

data class SupportingSheetAnchors(
    val peek: Dp,
    val browsing: Dp,
    val expanded: Dp,
    val canOverlay: Boolean,
)

fun supportingSheetAnchors(
    height: Dp,
    peekHeight: Dp = 112.dp,
): SupportingSheetAnchors {
    val expanded = (height - 180.dp).coerceAtLeast(0.dp)
    val peek = peekHeight.coerceAtMost(expanded)
    val canOverlay = expanded - peek >= 96.dp
    return SupportingSheetAnchors(
        peek,
        if (canOverlay) (height * .45f).coerceIn(peek + 48.dp, expanded - 48.dp) else peek,
        expanded,
        canOverlay,
    )
}
