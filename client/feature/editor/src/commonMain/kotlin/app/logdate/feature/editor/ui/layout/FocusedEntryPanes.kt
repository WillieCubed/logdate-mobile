package app.logdate.feature.editor.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun FocusedEntryPanes(
    focusedContent: @Composable () -> Unit,
    contextContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showContext = LocalEditorFocusPresentation.current.showSecondaryContext
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { focusedContent() } },
        endPane = { if (showContext) contextContent() },
        standardContent = {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) { focusedContent() }
                if (showContext) Box(Modifier.weight(0.7f).fillMaxHeight()) { contextContent() }
            }
        },
    )
}
