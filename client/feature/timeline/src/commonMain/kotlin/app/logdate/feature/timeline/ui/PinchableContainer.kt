@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.timeline.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun PinchableContainer(
    defaultContent: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
    animationLabel: String = "Pinchable Container",
) {
    var expanded by remember { mutableStateOf(false) }
    AnimatedContent(targetState = expanded, label = animationLabel) { isExpanded ->
        if (isExpanded) {
            expandedContent()
        } else {
            defaultContent()
        }
    }
}
