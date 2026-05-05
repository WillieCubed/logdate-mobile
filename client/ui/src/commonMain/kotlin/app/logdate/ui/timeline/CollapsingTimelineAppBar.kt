@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.ui.timeline

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.logdate.ui.sync.SyncIndicatorChip
import app.logdate.ui.sync.SyncPresentation
import logdate.client.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val BENCHMARK_TAG_HISTORY = "logdate_home_history"
private const val BENCHMARK_TAG_SEARCH = "logdate_home_search"
private const val BENCHMARK_TAG_SETTINGS = "logdate_home_settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    syncPresentation: SyncPresentation = SyncPresentation.Hidden,
    onSyncChipClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(Res.string.timeline),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        actions = {
            // Sync chip leads the action group when sync has something to say (syncing,
            // pending, network error). Composes nothing for Hidden / banner-promotion states.
            SyncIndicatorChip(
                presentation = syncPresentation,
                onClick = onSyncChipClick,
                modifier = Modifier.padding(end = 4.dp),
            )

            val historyLabel = stringResource(Res.string.location_history)
            val searchLabel = stringResource(Res.string.search)
            val settingsLabel = stringResource(Res.string.settings)

            IconButton(
                onClick = onHistoryClick,
                modifier =
                    Modifier.semantics {
                        contentDescription = "$historyLabel|$BENCHMARK_TAG_HISTORY"
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = historyLabel,
                )
            }
            IconButton(
                onClick = onSearchClick,
                modifier =
                    Modifier.semantics {
                        contentDescription = "$searchLabel|$BENCHMARK_TAG_SEARCH"
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = searchLabel,
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier =
                    Modifier.semantics {
                        contentDescription = "$settingsLabel|$BENCHMARK_TAG_SETTINGS"
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = settingsLabel,
                )
            }
        },
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    )
}
