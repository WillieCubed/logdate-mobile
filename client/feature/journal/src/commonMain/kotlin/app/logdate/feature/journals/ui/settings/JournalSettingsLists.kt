@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")
@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.settings

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.logdate.ui.theme.Spacing

/**
 * A settings screen that allows a user to change various properties of a journal.
 * Implements scroll behavior that adapts the top app bar to the scroll state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalSettingsStandardList(
    uiState: JournalSettingsUiState.Loaded,
    onShareJournal: (() -> Unit)?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            JournalOverviewSection(
                journal = uiState.journal,
                onShareJournal = onShareJournal,
            )
        }

        val insights = uiState.insights
        if (insights != null && insights.entryCount > 0) {
            item {
                JournalInsightsCard(
                    insights = insights,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }

        item {
            JournalNameField(
                journalName = uiState.editedName,
                onNameChange = onNameChange,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            JournalDescriptionField(
                description = uiState.editedDescription,
                onDescriptionChange = onDescriptionChange,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            JournalPrivacySettings(
                canShareJournal = onShareJournal != null,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            JournalDangerZone(
                onDeleteClick = onRequestDelete,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }

        item {
            Spacer(modifier = Modifier.padding(Spacing.lg))
        }
    }
}

@Composable
internal fun JournalSettingsOverviewPane(
    uiState: JournalSettingsUiState.Loaded,
    onShareJournal: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            JournalOverviewSection(
                journal = uiState.journal,
                onShareJournal = onShareJournal,
            )
        }

        val insights = uiState.insights
        if (insights != null && insights.entryCount > 0) {
            item {
                JournalInsightsCard(
                    insights = insights,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.padding(Spacing.lg))
        }
    }
}

@Composable
internal fun JournalSettingsEditPane(
    uiState: JournalSettingsUiState.Loaded,
    canShareJournal: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            JournalNameField(
                journalName = uiState.editedName,
                onNameChange = onNameChange,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            JournalDescriptionField(
                description = uiState.editedDescription,
                onDescriptionChange = onDescriptionChange,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            JournalPrivacySettings(
                canShareJournal = canShareJournal,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            JournalDangerZone(
                onDeleteClick = onRequestDelete,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            )
        }

        item {
            Spacer(modifier = Modifier.padding(Spacing.lg))
        }
    }
}
