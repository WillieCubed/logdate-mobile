package app.logdate.feature.editor.ui.common

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.feature.editor.ui.layout.LocalEditorIsCompact
import app.logdate.shared.model.Journal
import app.logdate.ui.theme.Spacing
import logdate.client.feature.editor.generated.resources.Res
import logdate.client.feature.editor.generated.resources.expand
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

/**
 * A Material You styled dropdown component to select multiple journals to associate an entry with.
 *
 * Renders a compact [FilterChip] when [LocalEditorIsCompact] is true (e.g. landscape phones),
 * and a full card otherwise.
 *
 * @param availableJournals List of all available journals
 * @param selectedJournalIds List of currently selected journal IDs
 * @param onSelectionChanged Callback when journal selection changes
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSelectorDropdown(
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    onSelectionChanged: (List<Uuid>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = LocalEditorIsCompact.current
    var expanded by remember { mutableStateOf(false) }
    val selectedCount = selectedJournalIds.size

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        if (isCompact) {
            CompactDropdownSelector(
                selectedCount = selectedCount,
                availableJournals = availableJournals,
                selectedJournalIds = selectedJournalIds,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
        } else {
            DropdownSelector(
                selectedCount = selectedCount,
                availableJournals = availableJournals,
                selectedJournalIds = selectedJournalIds,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
        }

        DropdownContent(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            availableJournals = availableJournals,
            selectedJournalIds = selectedJournalIds,
            onSelectionChanged = onSelectionChanged,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Compact chip anchor for height-constrained screens (e.g. landscape phones).
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactDropdownSelector(
    selectedCount: Int,
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    modifier: Modifier = Modifier,
) {
    val label =
        when (selectedCount) {
            0 -> "Select journal"
            1 ->
                availableJournals
                    .find { it.id == selectedJournalIds.first() }
                    ?.title
                    ?: "Unknown"
            else -> "$selectedCount journals"
        }

    FilterChip(
        selected = selectedCount > 0,
        onClick = {},
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedCount > 1) {
                    Badge { Text(selectedCount.toString()) }
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = stringResource(Res.string.expand),
                )
            }
        },
        modifier = modifier,
    )
}

/**
 * The selector card that displays the currently selected journals and triggers the dropdown.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun DropdownSelector(
    selectedCount: Int,
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selectedCount > 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            JournalIcon(selectedCount = selectedCount)

            Spacer(Modifier.width(12.dp))

            JournalSelectionText(
                selectedCount = selectedCount,
                availableJournals = availableJournals,
                selectedJournalIds = selectedJournalIds,
                modifier = Modifier.weight(1f),
            )

            DropdownIndicators(selectedCount = selectedCount)
        }
    }
}

/**
 * The journal icon displayed in the selector
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun JournalIcon(selectedCount: Int) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
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
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Text displaying information about the current journal selection
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun JournalSelectionText(
    selectedCount: Int,
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text =
                when (selectedCount) {
                    0 -> "Select journals"
                    1 -> {
                        val journalName =
                            availableJournals
                                .find { it.id == selectedJournalIds.first() }
                                ?.title
                        journalName ?: "Unknown journal"
                    }

                    else -> "Multiple journals"
                },
            style = MaterialTheme.typography.bodyLarge,
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

        if (selectedCount > 1) {
            Text(
                text = "$selectedCount journals selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * Displays indicator elements (badge and expand icon)
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun DropdownIndicators(selectedCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Selection count badge
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

        // Expand icon
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
 * The dropdown menu content showing available journals
 */
@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownContent(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    onSelectionChanged: (List<Uuid>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Use Material3 Surface for built-in elevation
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Handle empty state
            if (availableJournals.isEmpty()) {
                EmptyJournalItem()
            } else {
                // Journal items
                availableJournals.forEach { journal ->
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

/**
 * Menu item for when no journals are available
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyJournalItem() {
    DropdownMenuItem(
        text = {
            EmptyJournalItemContent()
        },
        onClick = { /* do nothing */ },
        colors =
            MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}

/**
 * Content for the empty journal item
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyJournalItemContent() {
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
}

/**
 * Menu item for each journal in the dropdown
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun JournalItem(
    journal: Journal,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            JournalItemContent(journal = journal, isSelected = isSelected)
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

/**
 * Content for each journal item
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun JournalItemContent(
    journal: Journal,
    isSelected: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
    ) {
        SelectionIndicator(isSelected = isSelected)

        Spacer(Modifier.width(Spacing.md))

        // Journal details
        JournalDetails(
            journal = journal,
            isSelected = isSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Selection indicator for a journal item
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun SelectionIndicator(isSelected: Boolean) {
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
}

/**
 * Journal title display component
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun JournalDetails(
    journal: Journal,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
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
        )
    }
}
