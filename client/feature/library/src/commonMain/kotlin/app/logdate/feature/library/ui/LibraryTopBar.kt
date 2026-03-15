@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.library.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.logdate.ui.SearchAppBar

/**
 * Top bar for the Library screen with a search field.
 */
@Composable
fun LibraryTopBar(modifier: Modifier = Modifier) {
    val (expanded, setExpanded) = remember { mutableStateOf(false) }

    SearchAppBar(
        hint = "Search library",
        expanded = expanded,
        onExpand = { setExpanded(true) },
        onNavigationClick = {},
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding(),
    )
}
