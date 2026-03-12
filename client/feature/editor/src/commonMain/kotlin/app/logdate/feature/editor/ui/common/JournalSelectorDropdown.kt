@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.editor.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.layout.LocalEditorIsCompact
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.Spacing
import kotlinx.coroutines.launch
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.expand
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * A Material You styled selector for associating an entry with one or more journals.
 *
 * When tapped, the card expands upward into a scrollable list as a floating surface — it does
 * not reflow surrounding content. The expansion is anchored at the bottom of the card so the
 * surface appears to rise in place. Predictive back gestures drive a real-time scale/fade on
 * the expanded surface, snapping back elastically on cancellation.
 *
 * Renders a compact variant when [LocalEditorIsCompact] is true (e.g. landscape phones).
 *
 * @param availableJournals All journals the user can assign the entry to.
 * @param selectedJournalIds IDs of journals currently associated with this entry.
 * @param onSelectionChanged Called with the full updated selection whenever the user toggles a journal.
 * @param modifier Modifier applied to the outer [AnchoredExpandingLayout].
 * @param initialExpanded Whether the picker starts in the expanded state. Intended for previews
 *   and screenshot tests; production callers should leave this at the default `false`.
 */
@Composable
fun JournalSelectorDropdown(
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    onSelectionChanged: (List<Uuid>) -> Unit,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initialExpanded) }
    val backProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    PlatformPredictiveBackHandler(
        enabled = expanded,
        onProgress = { progress -> scope.launch { backProgress.snapTo(progress) } },
        onBack = {
            scope.launch { backProgress.snapTo(0f) }
            expanded = false
        },
        onCancel = { scope.launch { backProgress.animateTo(0f, spring()) } },
    )

    AnchoredExpandingLayout(
        modifier = modifier,
        collapsedContent = {
            Card(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (selectedJournalIds.isNotEmpty()) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = JournalSelectorShape,
            ) {
                SelectorContent(
                    selectedCount = selectedJournalIds.size,
                    availableJournals = availableJournals,
                    selectedJournalIds = selectedJournalIds,
                )
            }
        },
        expandedContent = {
            AnimatedVisibility(
                visible = expanded,
                enter = journalSelectorEnterTransition(),
                exit = journalSelectorExitTransition(),
            ) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .journalSelectorExpandedHeight()
                            .graphicsLayer {
                                val scale = 1f - backProgress.value * 0.08f
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - backProgress.value * 0.3f
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            },
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = JournalSelectorShape,
                ) {
                    JournalPickerList(
                        availableJournals = availableJournals,
                        selectedJournalIds = selectedJournalIds,
                        onSelectionChanged = onSelectionChanged,
                    )
                }
            }
        },
    )
}

/**
 * A layout that reports only the [collapsedContent] height to its parent, then places
 * [expandedContent] above it anchored to the same bottom edge on a higher z-layer.
 *
 * This lets the expanded picker float over surrounding content without reflowing it —
 * the parent sees a stable footprint equal to the collapsed card.
 */
@Composable
private fun AnchoredExpandingLayout(
    modifier: Modifier = Modifier,
    collapsedContent: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            collapsedContent()
            expandedContent()
        },
    ) { measurables, constraints ->
        val collapsedPlaceable = measurables[0].measure(constraints)
        val expandedPlaceable = measurables[1].measure(constraints.copy(minHeight = 0))

        layout(collapsedPlaceable.width, collapsedPlaceable.height) {
            collapsedPlaceable.placeRelative(x = 0, y = 0)
            // Align expanded content's bottom to collapsed card's bottom so the surface
            // appears to rise upward from the same anchor point.
            expandedPlaceable.placeRelative(
                x = 0,
                y = collapsedPlaceable.height - expandedPlaceable.height,
                zIndex = 1f,
            )
        }
    }
}

/**
 * The collapsed trigger row: icon, journal name/count, and expand chevron.
 * Uses reduced sizes on height-constrained screens via [LocalEditorIsCompact].
 */
@Composable
private fun SelectorContent(
    selectedCount: Int,
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    modifier: Modifier = Modifier,
) {
    val isCompact = LocalEditorIsCompact.current
    val contentPadding = if (isCompact) 8.dp else 16.dp
    val iconCircleSize = if (isCompact) 24.dp else 40.dp
    val iconSize = if (isCompact) 14.dp else 20.dp

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier =
                Modifier
                    .size(iconCircleSize)
                    .clip(CircleShape)
                    .background(
                        if (selectedCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint =
                    if (selectedCount > 0) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(iconSize),
            )
        }

        Spacer(Modifier.width(12.dp))

        JournalSelectionText(
            selectedCount = selectedCount,
            availableJournals = availableJournals,
            selectedJournalIds = selectedJournalIds,
            modifier = Modifier.weight(1f),
        )

        SelectorIndicators(selectedCount = selectedCount)
    }
}

/**
 * Displays the current selection label inside the collapsed trigger row.
 *
 * Shows the journal title when exactly one is selected, a generic "Multiple journals" label
 * when more than one is selected, and a placeholder when nothing is selected yet.
 * A secondary line showing the count is shown for multi-selection on non-compact screens.
 */
@Composable
private fun JournalSelectionText(
    selectedCount: Int,
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    modifier: Modifier = Modifier,
) {
    val isCompact = LocalEditorIsCompact.current
    Column(modifier = modifier) {
        Text(
            text =
                when (selectedCount) {
                    0 -> "Select journals"
                    1 ->
                        availableJournals.find { it.id == selectedJournalIds.first() }?.title
                            ?: "Unknown journal"
                    else -> "Multiple journals"
                },
            style =
                if (isCompact) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            fontWeight = if (selectedCount > 0) FontWeight.Medium else FontWeight.Normal,
            color =
                if (selectedCount > 0) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (selectedCount > 1 && !isCompact) {
            Text(
                text = "$selectedCount journals selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Trailing section of the collapsed trigger: a count badge (when > 0 journals selected) and
 * a chevron icon. The badge and chevron tint track the selection state colour.
 */
@Composable
private fun SelectorIndicators(selectedCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selectedCount > 0) {
            Badge(
                modifier = Modifier.padding(end = 8.dp),
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = selectedCount.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Icon(
            Icons.Default.ExpandMore,
            contentDescription = stringResource(Res.string.expand),
            tint =
                if (selectedCount > 0) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * The expanded list of journals, shown as a floating surface rising above the selector card.
 * Capped at [journalSelectorExpandedHeight] (~4 items, screen-height-aware).
 */
@Composable
private fun JournalPickerList(
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    onSelectionChanged: (List<Uuid>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Journals",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        LazyColumn {
            if (availableJournals.isEmpty()) {
                item { EmptyJournalItem() }
            } else {
                items(availableJournals, key = { it.id.toString() }) { journal ->
                    val isSelected = selectedJournalIds.contains(journal.id)
                    JournalItem(
                        journal = journal,
                        isSelected = isSelected,
                        onClick = {
                            val newSelection = selectedJournalIds.toMutableList()
                            if (isSelected) {
                                newSelection.remove(journal.id)
                            } else {
                                newSelection.add(journal.id)
                            }
                            onSelectionChanged(newSelection)
                        },
                    )
                }
            }
        }
    }
}

/** Placeholder shown inside [JournalPickerList] when the user has no journals yet. */
@Composable
private fun EmptyJournalItem() {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    "No journals available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = {},
        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

/**
 * A single row in [JournalPickerList] representing one journal.
 *
 * A filled circular check indicator appears on the left when the journal is selected.
 * Tapping toggles the selection via [onClick]; the caller is responsible for updating state.
 */
@Composable
private fun JournalItem(
    journal: Journal,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Text(
                    text = journal.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        onClick = onClick,
        colors =
            MenuDefaults.itemColors(
                textColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
