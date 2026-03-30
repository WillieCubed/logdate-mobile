@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package app.logdate.feature.library.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import logdate.client.feature.library.generated.resources.Res
import logdate.client.feature.library.generated.resources.cd_search
import logdate.client.feature.library.generated.resources.search_library
import org.jetbrains.compose.resources.stringResource

/**
 * MD3 search bar for the Library screen.
 *
 * Displays a collapsed search bar that navigates to global search on tap,
 * matching the same component and layout as the journals search bar.
 */
@Composable
fun LibraryTopBar(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    val searchBarScope = rememberCoroutineScope()

    SearchBar(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                onSearch = {},
                placeholder = { Text(stringResource(Res.string.search_library)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(Res.string.cd_search),
                    )
                },
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}
