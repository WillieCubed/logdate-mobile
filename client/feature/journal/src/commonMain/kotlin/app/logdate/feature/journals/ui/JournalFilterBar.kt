@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.journals.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.cd_switch_to_carousel
import logdate.client.feature.journal.generated.resources.cd_switch_to_grid
import logdate.client.feature.journal.generated.resources.filter_owned_by_me
import logdate.client.feature.journal.generated.resources.filter_shared
import logdate.client.feature.journal.generated.resources.label_sorting_by
import logdate.client.feature.journal.generated.resources.sort_created
import logdate.client.feature.journal.generated.resources.sort_last_updated
import logdate.client.feature.journal.generated.resources.sort_title
import org.jetbrains.compose.resources.stringResource

/**
 * Pinned action bar above the journal list content.
 *
 * Horizontally scrollable row containing a layout mode toggle, sort dropdown, and filter chips.
 * The toggle button shows the *opposite* mode's icon as an affordance (e.g. grid icon when
 * in carousel mode). The background animates to `surfaceContainerHigh` when content scrolls
 * underneath, providing a visual separation cue. Only grid mode triggers this — carousel
 * content scrolls horizontally and never underlaps the bar.
 */
@Composable
fun JournalFilterBar(
    layoutMode: JournalLayoutMode,
    sortOption: JournalSortOption,
    activeFilters: Set<JournalFilter>,
    onToggleLayoutMode: () -> Unit,
    onSortOptionSelected: (JournalSortOption) -> Unit,
    onToggleFilter: (JournalFilter) -> Unit,
    modifier: Modifier = Modifier,
    isScrolled: Boolean = false,
) {
    val backgroundColor by animateColorAsState(
        targetValue =
            if (isScrolled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            },
        animationSpec = tween(200),
        label = "FilterBarBackground",
    )

    Row(
        modifier =
            modifier
                .background(backgroundColor)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onToggleLayoutMode) {
            Icon(
                imageVector =
                    when (layoutMode) {
                        JournalLayoutMode.CAROUSEL -> Icons.Default.GridView
                        JournalLayoutMode.GRID -> Icons.Default.ViewCarousel
                    },
                contentDescription =
                    when (layoutMode) {
                        JournalLayoutMode.CAROUSEL -> stringResource(Res.string.cd_switch_to_grid)
                        JournalLayoutMode.GRID -> stringResource(Res.string.cd_switch_to_carousel)
                    },
            )
        }

        SortDropdownChip(
            sortOption = sortOption,
            onSortOptionSelected = onSortOptionSelected,
        )

        FilterChip(
            selected = JournalFilter.OWNED_BY_ME in activeFilters,
            onClick = { onToggleFilter(JournalFilter.OWNED_BY_ME) },
            label = { Text(stringResource(Res.string.filter_owned_by_me)) },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        )

        FilterChip(
            selected = JournalFilter.SHARED in activeFilters,
            onClick = { onToggleFilter(JournalFilter.SHARED) },
            label = { Text(stringResource(Res.string.filter_shared)) },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        )
    }
}

@Composable
private fun SortDropdownChip(
    sortOption: JournalSortOption,
    onSortOptionSelected: (JournalSortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val sortLabel =
        when (sortOption) {
            JournalSortOption.LAST_UPDATED -> stringResource(Res.string.sort_last_updated)
            JournalSortOption.CREATED -> stringResource(Res.string.sort_created)
            JournalSortOption.TITLE -> stringResource(Res.string.sort_title)
        }

    AssistChip(
        onClick = { expanded = true },
        label = { Text(stringResource(Res.string.label_sorting_by, sortLabel)) },
        trailingIcon = {
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
            )
        },
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        JournalSortOption.entries.forEach { option ->
            val label =
                when (option) {
                    JournalSortOption.LAST_UPDATED -> stringResource(Res.string.sort_last_updated)
                    JournalSortOption.CREATED -> stringResource(Res.string.sort_created)
                    JournalSortOption.TITLE -> stringResource(Res.string.sort_title)
                }
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onSortOptionSelected(option)
                    expanded = false
                },
            )
        }
    }
}
