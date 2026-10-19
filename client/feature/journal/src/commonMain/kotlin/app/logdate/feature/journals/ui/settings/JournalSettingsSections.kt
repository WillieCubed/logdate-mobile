@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")
@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.settings

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.feature.journals.ui.JournalCover
import app.logdate.shared.model.Journal
import app.logdate.ui.common.AlertDialog
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.SimpleSettingsItem
import app.logdate.ui.theme.Spacing
import app.logdate.util.toReadableDateShort
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.danger_zone
import logdate.client.feature.journal.generated.resources.description
import logdate.client.feature.journal.generated.resources.enter_a_descriptive_name_for_your_journal
import logdate.client.feature.journal.generated.resources.journal_delete_action
import logdate.client.feature.journal.generated.resources.journal_delete_permanent_description
import logdate.client.feature.journal.generated.resources.journal_description_hint
import logdate.client.feature.journal.generated.resources.journal_insights_entries
import logdate.client.feature.journal.generated.resources.journal_insights_photos
import logdate.client.feature.journal.generated.resources.journal_insights_recordings
import logdate.client.feature.journal.generated.resources.journal_insights_texts
import logdate.client.feature.journal.generated.resources.journal_insights_title
import logdate.client.feature.journal.generated.resources.journal_insights_videos
import logdate.client.feature.journal.generated.resources.journal_name
import logdate.client.feature.journal.generated.resources.journal_share_action
import logdate.client.feature.journal.generated.resources.journal_visibility_private
import logdate.client.feature.journal.generated.resources.journal_visibility_shared_hint
import logdate.client.feature.journal.generated.resources.visibility
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_delete
import org.jetbrains.compose.resources.stringResource
import logdate.client.ui.generated.resources.Res as UiRes

/**
 * A settings screen that allows a user to change various properties of a journal.
 * Implements scroll behavior that adapts the top app bar to the scroll state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalInsightsCard(
    insights: JournalInsights,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.journal_insights_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(Res.string.journal_insights_entries, insights.entryCount),
                style = MaterialTheme.typography.headlineMedium,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (insights.textCount > 0) {
                    Text(
                        text = stringResource(Res.string.journal_insights_texts, insights.textCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (insights.imageCount > 0) {
                    Text(
                        text = stringResource(Res.string.journal_insights_photos, insights.imageCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (insights.videoCount > 0) {
                    Text(
                        text = stringResource(Res.string.journal_insights_videos, insights.videoCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (insights.audioCount > 0) {
                    Text(
                        text = stringResource(Res.string.journal_insights_recordings, insights.audioCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (insights.oldestEntry != null && insights.newestEntry != null) {
                Text(
                    text = "${insights.oldestEntry.toReadableDateShort()} — ${insights.newestEntry.toReadableDateShort()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun JournalPrivacySettings(
    canShareJournal: Boolean,
    modifier: Modifier = Modifier,
) {
    SettingsSection(title = stringResource(Res.string.visibility), modifier = modifier) {
        SimpleSettingsItem(
            title = stringResource(Res.string.journal_visibility_private),
            description = if (canShareJournal) stringResource(Res.string.journal_visibility_shared_hint) else null,
            action = {},
        )
    }
}

@Composable
internal fun JournalDangerZone(
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(Res.string.danger_zone),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )

        ElevatedButton(
            onClick = onDeleteClick,
            colors =
                ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(Res.string.journal_delete_action),
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Text(stringResource(Res.string.journal_delete_action))
        }
    }
}

@Composable
internal fun JournalOverviewSection(
    journal: Journal,
    modifier: Modifier = Modifier,
    onShareJournal: (() -> Unit)? = null,
) {
    // Use BoxWithConstraints to create a responsive layout
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        // Calculate the available space
        val availableWidth = maxWidth

        // Container with 1:1 aspect ratio
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(AspectRatios.RATIO_1_1),
            // Using raw ratio since this is just a container
            contentAlignment = Alignment.Center,
        ) {
            // Use Column with space between to position elements
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Journal cover in a constrained box
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // Use a fixed width for the cover based on container size
                    JournalCover(
                        journal = journal,
                        // Apply constraints rather than percentage
                        modifier =
                            Modifier
                                .widthIn(max = availableWidth - Spacing.xl)
                                .padding(Spacing.md),
                        elevation = 4.dp,
                    )
                }

                // Fixed spacing
                Spacer(modifier = Modifier.padding(Spacing.sm))

                // A journal link reaches someone else only when the server hosts shared journals.
                if (onShareJournal != null) {
                    ElevatedButton(
                        onClick = onShareJournal,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(Res.string.journal_share_action),
                            modifier = Modifier.padding(end = Spacing.sm),
                        )
                        Text(stringResource(Res.string.journal_share_action))
                    }
                }
            }
        }
    }
}

@Composable
internal fun JournalNameField(
    journalName: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = journalName,
        onValueChange = { onNameChange(it) },
        label = { Text(stringResource(Res.string.journal_name)) },
        textStyle = MaterialTheme.typography.headlineMedium,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = {
            Text(stringResource(Res.string.enter_a_descriptive_name_for_your_journal))
        },
    )
}

@Composable
internal fun JournalDescriptionField(
    description: String,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = description,
        onValueChange = { onDescriptionChange(it) },
        label = { Text(stringResource(Res.string.description)) },
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
        minLines = 2,
        supportingText = {
            Text(stringResource(Res.string.journal_description_hint))
        },
    )
}

/**
 * A confirmation dialog for deleting a journal.
 *
 * @param onDismissRequest Callback to be invoked when the dialog is dismissed.
 * @param onConfirmation Callback to be invoked when the user confirms the deletion.
 */
@Composable
fun DeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    AlertDialog(
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
            )
        },
        title = {
            Text(stringResource(Res.string.journal_delete_action))
        },
        text = {
            Text(
                stringResource(Res.string.journal_delete_permanent_description),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmation,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringResource(UiRes.string.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(UiRes.string.common_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
