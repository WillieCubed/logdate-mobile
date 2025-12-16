package app.logdate.feature.journals.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.logdate.ui.SearchAppBar
import app.logdate.ui.theme.Spacing

/**
 * A search toolbar for the journals screen.
 */
@Composable
fun JournalSearchToolbar(
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (expanded, setExpanded) = remember { mutableStateOf(false) }
    
    SearchAppBar(
        hint = "Search journals",
        expanded = expanded,
        onExpand = { setExpanded(true) },
        onNavigationClick = onNavigationClick,
        modifier = modifier.fillMaxWidth()
    )
}