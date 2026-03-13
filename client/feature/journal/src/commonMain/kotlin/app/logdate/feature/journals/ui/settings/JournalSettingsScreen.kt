@file:Suppress("ktlint:standard:function-naming", "ktlint:standard:no-wildcard-imports")
@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.logdate.feature.journals.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.logdate.feature.journals.ui.JournalCover
import app.logdate.shared.model.Journal
import app.logdate.ui.common.AspectRatios
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.*
import logdate.client.feature.journal.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

/**
 * A settings screen that allows a user to change various properties of a journal.
 * Implements scroll behavior that adapts the top app bar to the scroll state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSettingsScreen(
    journalId: Uuid,
    onGoBack: () -> Unit,
    onJournalDeleted: () -> Unit = {},
    viewModel: JournalSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var openDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    // Set the journal ID when the screen is mounted
    LaunchedEffect(journalId) {
        viewModel.setSelectedJournalId(journalId)
    }

    // Create a scroll behavior for the top app bar
    JournalSettingsScreenContent(
        uiState = state,
        onGoBack = onGoBack,
        onNameChange = viewModel::updateJournalName,
        onSaveChanges = { viewModel.saveJournalChanges { onGoBack() } },
        onShareJournal = viewModel::shareJournal,
        onRequestDelete = { openDeleteConfirmation = true },
        showDeleteConfirmation = openDeleteConfirmation,
        onDismissDeleteConfirmation = { openDeleteConfirmation = false },
        onConfirmDelete = {
            viewModel.deleteJournal {
                openDeleteConfirmation = false
                onJournalDeleted()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSettingsScreenContent(
    uiState: JournalSettingsUiState,
    onGoBack: () -> Unit,
    onNameChange: (String) -> Unit = {},
    onSaveChanges: () -> Unit = {},
    onShareJournal: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    showDeleteConfirmation: Boolean = false,
    onDismissDeleteConfirmation: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    when (uiState) {
        is JournalSettingsUiState.Unknown -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(Res.string.loading))
            }
        }

        is JournalSettingsUiState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.journal_settings)) },
                        navigationIcon = {
                            IconButton(onClick = onGoBack) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = stringResource(Res.string.back),
                                )
                            }
                        },
                        actions = {
                            // Show save button with attractive animation when there are unsaved changes
                            val visibilityState =
                                remember(uiState.hasUnsavedChanges) {
                                    MutableTransitionState(!uiState.hasUnsavedChanges).apply {
                                        targetState = uiState.hasUnsavedChanges
                                    }
                                }

                            AnimatedVisibility(
                                visibleState = visibilityState,
                                enter =
                                    fadeIn(
                                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                                    ) +
                                        scaleIn(
                                            initialScale = 0.8f,
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow,
                                                ),
                                        ),
                                exit =
                                    scaleOut(
                                        targetScale = 0.8f,
                                        animationSpec = tween(100),
                                    ) +
                                        fadeOut(
                                            animationSpec = tween(100),
                                        ),
                            ) {
                                // Use an IconButton for a cleaner, more compact appearance
                                IconButton(
                                    onClick = onSaveChanges,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = stringResource(Res.string.save_changes),
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
                modifier =
                    modifier
                        .applyScreenStyles()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) { paddingValues ->
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // Journal overview section
                    item {
                        JournalOverviewSection(
                            journal = uiState.journal,
                            onShareJournal = onShareJournal,
                        )
                    }

                    // Journal name section
                    item {
                        JournalNameField(
                            journalName = uiState.editedName,
                            onNameChange = onNameChange,
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }

                    // Journal privacy settings
                    item {
                        JournalPrivacySettings(
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        )
                    }

                    // Danger zone
                    item {
                        JournalDangerZone(
                            onDeleteClick = onRequestDelete,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        )
                    }

                    // Add bottom spacing
                    item {
                        Spacer(modifier = Modifier.padding(Spacing.lg))
                    }
                }
            }

            if (showDeleteConfirmation) {
                DeleteConfirmationDialog(
                    onDismissRequest = onDismissDeleteConfirmation,
                    onConfirmation = onConfirmDelete,
                )
            }
        }
    }
}

@Composable
private fun JournalPrivacySettings(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(Res.string.privacy),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // Privacy settings content would go here
        Text(
            text = stringResource(Res.string.journal_privacy_settings_will_appear_here_in_future_updates),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun JournalDangerZone(
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
                contentDescription = stringResource(Res.string.delete_journal),
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Text(stringResource(Res.string.delete_journal))
        }
    }
}

@Composable
private fun JournalOverviewSection(
    journal: Journal,
    modifier: Modifier = Modifier,
    onShareJournal: () -> Unit = {},
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

                // Button sized to its content
                ElevatedButton(
                    onClick = onShareJournal,
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(Res.string.share_journal),
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                    Text(stringResource(Res.string.share_journal))
                }
            }
        }
    }
}

@Composable
private fun JournalNameField(
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
            Text(stringResource(Res.string.delete_journal))
        },
        text = {
            Text(
                "Are you sure you want to delete this journal? This action cannot be undone and all entries in this journal will be permanently deleted.",
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
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
