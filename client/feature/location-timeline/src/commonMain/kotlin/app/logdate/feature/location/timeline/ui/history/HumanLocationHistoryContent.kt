@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.location.timeline.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import logdate.client.feature.location.timeline.generated.resources.Res
import logdate.client.feature.location.timeline.generated.resources.history_add_visit
import logdate.client.feature.location.timeline.generated.resources.history_collapse_map
import logdate.client.feature.location.timeline.generated.resources.history_details
import logdate.client.feature.location.timeline.generated.resources.history_empty_day
import logdate.client.feature.location.timeline.generated.resources.history_empty_day_body
import logdate.client.feature.location.timeline.generated.resources.history_expand_map
import logdate.client.feature.location.timeline.generated.resources.history_hide_replay
import logdate.client.feature.location.timeline.generated.resources.history_next_day
import logdate.client.feature.location.timeline.generated.resources.history_next_stop
import logdate.client.feature.location.timeline.generated.resources.history_pause
import logdate.client.feature.location.timeline.generated.resources.history_play
import logdate.client.feature.location.timeline.generated.resources.history_previous_day
import logdate.client.feature.location.timeline.generated.resources.history_previous_stop
import logdate.client.feature.location.timeline.generated.resources.history_recover
import logdate.client.feature.location.timeline.generated.resources.history_replay
import logdate.client.feature.location.timeline.generated.resources.history_your_day
import logdate.client.feature.location.timeline.generated.resources.history_your_places
import org.jetbrains.compose.resources.stringResource

@Composable
fun HumanLocationHistoryContent(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit = {},
) {
    Surface(modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 1100.dp).fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    HistoryTab.entries.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { actions.onTab(tab) },
                            text = {
                                Text(
                                    stringResource(
                                        if (tab ==
                                            HistoryTab.Day
                                        ) {
                                            Res.string.history_your_day
                                        } else {
                                            Res.string.history_your_places
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                state.recoveryMessage?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                            actions.onRecover?.let { recover ->
                                TextButton(
                                    onClick = recover,
                                ) { Text(state.recoveryActionLabel ?: stringResource(Res.string.history_recover)) }
                            }
                        }
                    }
                }
                if (state.tab == HistoryTab.Day) {
                    HistoryDay(state, actions, mapContent)
                } else {
                    HistoryPlaces(state, actions, mapContent)
                }
            }
        }
    }
    if (state.detailVisible) {
        state.selectedItem?.let { HistoryDetail(it, actions) }
    }
}

@Composable
private fun HistoryDay(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
    mapContent: @Composable (Modifier) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        HistoryDateHeader(state, actions)
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxWidth >= 720.dp && state.items.isNotEmpty()) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.4f)) {
                        HistoryDayOverview(state, actions, mapContent, expandedByDefault = true)
                    }
                    HistoryDayList(state, actions, Modifier.weight(0.6f).fillMaxHeight())
                }
            } else {
                HistoryDayList(state, actions, Modifier.fillMaxSize()) {
                    if (state.items.isNotEmpty()) {
                        HistoryDayOverview(state, actions, mapContent)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDateHeader(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (largeText) HistoryDateButton(state, actions, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (!largeText) HistoryDateButton(state, actions, Modifier.fillMaxWidth())
                if (state.daySummary.isNotBlank()) {
                    Text(state.daySummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { actions.onDayOffset(-1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.history_previous_day))
            }
            IconButton(onClick = { actions.onDayOffset(1) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(Res.string.history_next_day))
            }
        }
    }
}

@Composable
private fun HistoryDateButton(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
    modifier: Modifier,
) {
    TextButton(onClick = actions.onCalendar, modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Text(
            state.dateLabel,
            Modifier.weight(1f),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Icon(Icons.Default.CalendarMonth, null, Modifier.padding(horizontal = 8.dp).size(18.dp))
    }
}

@Composable
private fun HistoryDayOverview(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
    mapContent: @Composable (Modifier) -> Unit,
    expandedByDefault: Boolean = false,
) {
    var expanded by remember { mutableStateOf(expandedByDefault) }
    Box(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).animateContentSize()) {
        mapContent(Modifier.fillMaxWidth().height(if (expanded) 280.dp else 96.dp))
    }
    HistoryReplay(state, actions) {
        TextButton(onClick = { expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
            Text(
                stringResource(if (expanded) Res.string.history_collapse_map else Res.string.history_expand_map),
                Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun HistoryDayList(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
    modifier: Modifier,
    header: @Composable () -> Unit = {},
) {
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { header() }
        if (state.items.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.history_empty_day), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.history_empty_day_body))
                }
            }
        }
        itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
            HistoryTimelineItem(
                item,
                state.selectedItemId == item.id,
                actions,
                Modifier.padding(horizontal = 16.dp),
                isLast = index == state.items.lastIndex,
                isFirst = index == 0,
            )
        }
        item {
            TextButton(
                onClick = { actions.onEdit(null, HistoryEditAction.AddVisit) },
                modifier = Modifier.padding(start = 60.dp, top = 8.dp),
            ) {
                Text(stringResource(Res.string.history_add_visit))
            }
        }
    }
}

@Composable
private fun HistoryReplay(
    state: HumanLocationHistoryState,
    actions: HumanLocationHistoryActions,
    mapAction: @Composable () -> Unit,
) {
    val replayLabel = stringResource(Res.string.history_replay)
    var expanded by remember(state.dateLabel) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                if (expanded) actions.onReplayPlaying?.invoke(false)
                expanded = !expanded
            }) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Text(if (expanded) stringResource(Res.string.history_hide_replay) else replayLabel, Modifier.padding(start = 4.dp))
            }
            mapAction()
        }
        if (!expanded) return@Column
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(replayLabel, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            actions.onReplayPlaying?.let { onPlay ->
                TextButton(onClick = { onPlay(!state.replayPlaying) }) {
                    Text(stringResource(if (state.replayPlaying) Res.string.history_pause else Res.string.history_play))
                }
            }
            IconButton(
                onClick = { state.adjacentItem(-1)?.let { actions.onSelectItem(it.id) } },
                enabled = state.adjacentItem(-1) != null,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.history_previous_stop)) }
            IconButton(
                onClick = { state.adjacentItem(1)?.let { actions.onSelectItem(it.id) } },
                enabled = state.adjacentItem(1) != null,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(Res.string.history_next_stop)) }
        }
        val selectedIndex = state.items.indexOfFirst { it.id == state.selectedItemId }.coerceAtLeast(0)
        Slider(
            value = selectedIndex.toFloat() / (state.items.size - 1).coerceAtLeast(1),
            onValueChange = { position -> state.itemAtReplayPosition(position)?.let { actions.onSelectItem(it.id) } },
            steps = (state.items.size - 2).coerceAtLeast(0),
            enabled = state.items.size > 1,
            modifier = Modifier.semantics { contentDescription = replayLabel },
        )
        state.selectedItem?.let { item ->
            Column(
                Modifier.fillMaxWidth().testTag("history-replay-preview"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.timeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.memories.take(2).forEach { HistoryMemoryPreview(it, actions.onOpenMemory) }
                TextButton(onClick = { actions.onOpenDetail(item.id) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(Res.string.history_details))
                }
            }
        }
    }
}
