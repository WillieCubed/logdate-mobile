@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")

package app.logdate.ui.timeline

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.logdate.ui.platform.PlatformIcons
import app.logdate.ui.platform.currentPlatform
import app.logdate.ui.sync.SyncIndicatorChip
import app.logdate.ui.sync.SyncPresentation
import logdate.client.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private const val BENCHMARK_TAG_HISTORY = "logdate_home_history"
private const val BENCHMARK_TAG_SEARCH = "logdate_home_search"
private const val BENCHMARK_TAG_SETTINGS = "logdate_home_settings"
private const val BENCHMARK_TAG_NEW_ENTRY = "logdate_home_new_entry"

/**
 * Top app bar for the home timeline.
 *
 * On iPhone, iPad, and Mac Catalyst the bar renders large at rest and collapses to a small
 * title as the user scrolls, mirroring Apple's standard large-title pattern. Android keeps
 * the small fixed-title bar that matches the rest of the app's Material chrome.
 *
 * On hosts where the floating create button is hidden (currently iOS / iPadOS), pass a
 * non-null [onNewEntry] so the bar renders a trailing "new entry" action as the replacement
 * affordance. Other hosts keep [onNewEntry] null and rely on their own create paths.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onNewEntry: (() -> Unit)? = null,
    syncPresentation: SyncPresentation = SyncPresentation.Hidden,
    onSyncChipClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val title: @Composable () -> Unit = {
        Text(
            text = stringResource(Res.string.timeline),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    if (currentPlatform.isApple) {
        LargeTopAppBar(
            title = title,
            actions = {
                TimelineActions(
                    onSearchClick = onSearchClick,
                    onSettingsClick = onSettingsClick,
                    onHistoryClick = onHistoryClick,
                    onNewEntry = onNewEntry,
                    syncPresentation = syncPresentation,
                    onSyncChipClick = onSyncChipClick,
                )
            },
            scrollBehavior = scrollBehavior,
            modifier = modifier,
        )
    } else {
        TopAppBar(
            title = title,
            actions = {
                TimelineActions(
                    onSearchClick = onSearchClick,
                    onSettingsClick = onSettingsClick,
                    onHistoryClick = onHistoryClick,
                    onNewEntry = onNewEntry,
                    syncPresentation = syncPresentation,
                    onSyncChipClick = onSyncChipClick,
                )
            },
            scrollBehavior = scrollBehavior,
            modifier = modifier,
        )
    }
}

@Composable
private fun RowScope.TimelineActions(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onNewEntry: (() -> Unit)?,
    syncPresentation: SyncPresentation,
    onSyncChipClick: () -> Unit,
) {
    // Sync chip leads the action group when sync has something to say (syncing, pending,
    // network error). Composes nothing for Hidden / banner-promotion states.
    SyncIndicatorChip(
        presentation = syncPresentation,
        onClick = onSyncChipClick,
        modifier = Modifier.padding(end = 4.dp),
    )

    val historyLabel = stringResource(Res.string.location_history)
    val searchLabel = stringResource(Res.string.search)
    val settingsLabel = stringResource(Res.string.settings)
    val newEntryLabel = stringResource(Res.string.create_new_entry)

    IconButton(
        onClick = onHistoryClick,
        modifier = Modifier.testTag(BENCHMARK_TAG_HISTORY),
    ) {
        Icon(
            painter = PlatformIcons.history(),
            contentDescription = historyLabel,
        )
    }
    IconButton(
        onClick = onSearchClick,
        modifier = Modifier.testTag(BENCHMARK_TAG_SEARCH),
    ) {
        Icon(
            painter = PlatformIcons.search(),
            contentDescription = searchLabel,
        )
    }
    IconButton(
        onClick = onSettingsClick,
        modifier = Modifier.testTag(BENCHMARK_TAG_SETTINGS),
    ) {
        Icon(
            painter = PlatformIcons.settings(),
            contentDescription = settingsLabel,
        )
    }
    if (onNewEntry != null) {
        IconButton(
            onClick = onNewEntry,
            modifier = Modifier.testTag(BENCHMARK_TAG_NEW_ENTRY),
        ) {
            Icon(
                painter = PlatformIcons.newEntry(),
                contentDescription = newEntryLabel,
            )
        }
    }
}
