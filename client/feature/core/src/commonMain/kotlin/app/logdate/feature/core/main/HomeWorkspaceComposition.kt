@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.logdate.ui.workspace.AdaptiveWorkspaceLayout
import app.logdate.ui.workspace.PanelConstraints
import app.logdate.ui.workspace.WorkspaceDestination
import app.logdate.ui.workspace.WorkspaceRailJournals
import app.logdate.ui.workspace.WorkspaceScaffold
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.workspace_add_a_memory
import org.jetbrains.compose.resources.stringResource

/** Home's shared navigation host and panel composition, used by live routes and populated scenes. */
@Composable
fun HomeWorkspaceComposition(
    destinations: List<WorkspaceDestination>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
    createLabel: String = stringResource(Res.string.workspace_add_a_memory),
    createEntryTransition: Boolean = true,
    onSearch: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    status: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    journals: WorkspaceRailJournals? = null,
    createOnPull: Boolean = false,
    content: @Composable () -> Unit,
) {
    WorkspaceScaffold(
        destinations,
        selectedKey,
        onSelect,
        modifier,
        onCreate,
        createLabel,
        createEntryTransition,
        onSearch,
        actions,
        status,
        snackbarHost = snackbarHost,
        journals = journals,
        createOnPull = createOnPull,
    ) {
        if (selectedKey == HomeRouteDestination.LocationHistory.name) {
            content()
        } else {
            AdaptiveWorkspaceLayout(
                Modifier.fillMaxSize(),
                browseOnStart = true,
                focusConstraints =
                    when (selectedKey) {
                        HomeRouteDestination.Timeline.name -> PanelConstraints.ReadingCollection
                        HomeRouteDestination.Library.name -> PanelConstraints.Visual
                        else -> PanelConstraints.Reading
                    },
                focus = content,
            )
        }
    }
}
