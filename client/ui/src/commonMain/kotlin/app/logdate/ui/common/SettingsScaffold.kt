@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.logdate.ui.theme.Spacing
import logdate.client.ui.generated.resources.Res
import logdate.client.ui.generated.resources.back
import org.jetbrains.compose.resources.stringResource

/**
 * Shared scaffold for settings screens.
 *
 * Encapsulates the common boilerplate: collapsing [LargeTopAppBar] with a back button,
 * [DefaultSettingsContentContainer] width constraint, navigation-bar insets, and a
 * [LazyColumn] with standard spacing. Screens supply only their title and list items.
 *
 * @param title The screen title shown in the collapsing top bar.
 * @param onBack Callback when the user taps the back arrow.
 * @param modifier Modifier applied to the root scaffold.
 * @param snackbarHostState Optional snackbar host; when non-null, a [SnackbarHost] is shown.
 * @param topBarColors Optional custom colors for the [LargeTopAppBar].
 * @param content Items to display inside the [LazyColumn].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    topBarColors: TopAppBarColors? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = topBarColors ?: TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = {
            if (snackbarHostState != null) {
                SnackbarHost(snackbarHostState)
            }
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                content()
            }
        }
    }
}
