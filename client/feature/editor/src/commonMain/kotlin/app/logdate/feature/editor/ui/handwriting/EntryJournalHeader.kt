@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.handwriting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.layout.LocalEntryChromeHeight
import app.logdate.shared.model.Journal
import app.logdate.ui.common.DropdownMenu
import app.logdate.ui.theme.Spacing
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.close
import logdate.client.feature.editor.generated.resources.entry_page_choose_journal
import logdate.client.feature.editor.generated.resources.entry_page_drafts_count
import logdate.client.feature.editor.generated.resources.entry_page_entry
import logdate.client.feature.editor.generated.resources.entry_page_options
import logdate.client.feature.editor.generated.resources.entry_page_save
import logdate.client.feature.editor.generated.resources.save_entry
import logdate.client.feature.editor.generated.resources.saving
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EntryJournalHeader(
    journal: Journal?,
    journalSelected: Boolean,
    availableJournals: List<Journal>,
    onSelectJournal: ((Journal) -> Unit)?,
    onJournalSelection: () -> Unit,
    onExit: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saveInProgress: Boolean,
    onShowDrafts: (() -> Unit)?,
    draftCount: Int,
    floating: Boolean,
    modifier: Modifier = Modifier,
) {
    var journalOpen by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val entryTitle = stringResource(Res.string.entry_page_entry)
    val saveDescription = stringResource(Res.string.save_entry)
    val savingDescription = stringResource(Res.string.saving)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 360.dp
        EntryHeaderSlots(
            Modifier
                .fillMaxWidth()
                .height(LocalEntryChromeHeight.current)
                .padding(horizontal = Spacing.lg)
                .testTag("entry_mode_overview")
                .semantics { paneTitle = entryTitle },
            start = {
                FilledTonalIconButton(
                    onClick = onExit,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = StoryChrome),
                ) { Icon(Icons.Outlined.Close, stringResource(Res.string.close)) }
            },
            center = {
                Box {
                    Column(
                        Modifier
                            .then(if (floating) Modifier.background(StoryChrome, RoundedCornerShape(24.dp)) else Modifier)
                            .heightIn(min = 48.dp)
                            .clickable { journalOpen = true }
                            .padding(horizontal = Spacing.sm)
                            .testTag("entry_journal_chooser"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (!largeText && !floating) {
                            Text(entryTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (journalSelected) journal?.title.orEmpty() else stringResource(Res.string.entry_page_choose_journal),
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelMedium,
                                color = StoryMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = StoryMuted)
                        }
                    }
                    DropdownMenu(journalOpen, onDismissRequest = { journalOpen = false }) {
                        availableJournals.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.title) },
                                trailingIcon = { if (journalSelected && item.id == journal?.id) Icon(Icons.Outlined.Check, null) },
                                onClick = {
                                    onSelectJournal?.invoke(item) ?: onJournalSelection()
                                    journalOpen = false
                                },
                            )
                        }
                    }
                }
            },
            end = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val saveModifier =
                        Modifier.height(40.dp).semantics {
                            contentDescription = saveDescription
                            if (saveInProgress) stateDescription = savingDescription
                            liveRegion = LiveRegionMode.Polite
                        }
                    if (narrow) {
                        FilledIconButton(
                            onClick = onSave,
                            enabled = saveEnabled && !saveInProgress,
                            modifier = saveModifier.size(40.dp),
                        ) {
                            if (saveInProgress) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
                            }
                        }
                    } else {
                        Button(
                            onClick = onSave,
                            enabled = saveEnabled && !saveInProgress,
                            modifier = saveModifier,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            if (saveInProgress) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else if (largeText) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
                            } else {
                                Text(stringResource(Res.string.entry_page_save), maxLines = 1)
                            }
                        }
                    }
                    if (onShowDrafts != null) {
                        Box {
                            FilledTonalIconButton(onClick = { optionsOpen = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Outlined.MoreVert, stringResource(Res.string.entry_page_options))
                            }
                            DropdownMenu(optionsOpen, { optionsOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.entry_page_drafts_count, draftCount)) },
                                    enabled = draftCount > 0,
                                    modifier = Modifier.testTag("entry_open_drafts"),
                                    onClick = {
                                        optionsOpen = false
                                        onShowDrafts()
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/** Equal measured side reservations center the chooser on the safe header, even with more trailing actions. */
@Composable
private fun EntryHeaderSlots(
    modifier: Modifier,
    start: @Composable () -> Unit,
    center: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    Layout(content = {
        Box { start() }
        Box { center() }
        Box { end() }
    }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leading = measurables[0].measure(loose)
        val trailing = measurables[2].measure(loose)
        val side = maxOf(leading.width, trailing.width) + Spacing.sm.roundToPx()
        val middle = measurables[1].measure(loose.copy(maxWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            leading.placeRelative(0, (constraints.maxHeight - leading.height) / 2)
            middle.placeRelative((constraints.maxWidth - middle.width) / 2, (constraints.maxHeight - middle.height) / 2)
            trailing.placeRelative(constraints.maxWidth - trailing.width, (constraints.maxHeight - trailing.height) / 2)
        }
    }
}
