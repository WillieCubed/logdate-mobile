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
 * Renders a compact variant when [LocalEditorIsCompact] is true (e.g. landscape phones),
 * using reduced padding and icon sizes while keeping the same visual language.
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
    var expanded by remember { mutableStateOf(false) }
    val selectedCount = selectedJournalIds.size

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        DropdownSelector(
            selectedCount = selectedCount,
            availableJournals = availableJournals,
            selectedJournalIds = selectedJournalIds,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

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
 * The selector card that displays the currently selected journals and triggers the dropdown.
 *
 * Automatically uses reduced padding and icon sizes on height-constrained screens
 * (e.g. landscape phones) via [LocalEditorIsCompact].
 */
@Suppress("ktlint:standard:function-naming")
@Composable
private fun DropdownSelector(
    selectedCount: Int,
    availableJournals: List<Journal>,
    selectedJournalIds: List<Uuid>,
    modifier: Modifier = Modifier,
) {
    val isCompact = LocalEditorIsCompact.current
    val contentPadding = if (isCompact) 8.dp else 16.dp
    val iconCircleSize = if (isCompact) 24.dp else 40.dp
    val iconSize = if (isCompact) 14.dp else 20.dp

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

            DropdownIndicators(selectedCount = selectedCount)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
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
                    1 -> {
                        availableJournals
                            .find { it.id == selectedJournalIds.first() }
                            ?.title
                            ?: "Unknown journal"
                    }
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

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DropdownIndicators(selectedCount: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            if (availableJournals.isEmpty()) {
                EmptyJournalItem()
            } else {
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

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyJournalItem() {
    DropdownMenuItem(
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(4.dp),
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

@Suppress("ktlint:standard:function-naming")
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
                modifier = Modifier.fillMaxWidth().padding(4.dp),
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
