@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.ui.common

import androidx.compose.foundation.MutatePriority
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.material3.TooltipBox as MaterialTooltipBox

/** Keeps the trigger composed while immediately concealing a private tooltip at app lock. */
@Composable
fun TooltipBox(
    positionProvider: PopupPositionProvider,
    tooltip: @Composable TooltipScope.() -> Unit,
    state: TooltipState,
    modifier: Modifier = Modifier,
    onDismissRequest: (() -> Unit)? = null,
    focusable: Boolean = false,
    enableUserInput: Boolean = true,
    hasAction: Boolean = false,
    content: @Composable () -> Unit,
) {
    val visible = rememberUpdatedState(LocalPrivateModalVisible.current)
    val guardedState =
        remember(state) {
            object : TooltipState by state {
                override val isVisible: Boolean
                    get() = visible.value && state.isVisible

                override suspend fun show(mutatePriority: MutatePriority) {
                    if (visible.value) state.show(mutatePriority)
                }
            }
        }
    LaunchedEffect(visible.value, state) {
        if (!visible.value) state.dismiss()
    }
    MaterialTooltipBox(
        positionProvider = positionProvider,
        tooltip = { if (visible.value) tooltip() },
        state = guardedState,
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        focusable = focusable,
        enableUserInput = enableUserInput && visible.value,
        hasAction = hasAction,
        content = content,
    )
}
