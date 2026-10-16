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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.journal.generated.resources.Res
import logdate.client.feature.journal.generated.resources.journal_delete_action
import logdate.client.feature.journal.generated.resources.journal_settings_title
import logdate.client.feature.journal.generated.resources.save_changes
import logdate.client.ui.generated.resources.common_back
import logdate.client.ui.generated.resources.common_cancel
import logdate.client.ui.generated.resources.common_delete
import logdate.client.ui.generated.resources.common_loading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid
import logdate.client.ui.generated.resources.Res as UiRes

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

    LaunchedEffect(journalId) {
        viewModel.setSelectedJournalId(journalId)
    }

    JournalSettingsScreenContent(
        uiState = state,
        onGoBack = onGoBack,
        onNameChange = viewModel::updateJournalName,
        onDescriptionChange = viewModel::updateJournalDescription,
        onSaveChanges = { viewModel.saveJournalChanges { onGoBack() } },
        onShareJournal = if (viewModel.canShareJournalLink) viewModel::shareJournal else null,
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
    onDescriptionChange: (String) -> Unit = {},
    onSaveChanges: () -> Unit = {},
    onShareJournal: (() -> Unit)? = null,
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
                Text(stringResource(UiRes.string.common_loading))
            }
        }

        is JournalSettingsUiState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.journal_settings_title)) },
                        navigationIcon = {
                            IconButton(onClick = onGoBack) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = stringResource(UiRes.string.common_back),
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
                contentWindowInsets = WindowInsets.navigationBars,
            ) { paddingValues ->
                FoldableBookLayout(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    minPaneWidth = 320.dp,
                    startPane = {
                        JournalSettingsOverviewPane(
                            uiState = uiState,
                            onShareJournal = onShareJournal,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    endPane = {
                        JournalSettingsEditPane(
                            uiState = uiState,
                            canShareJournal = onShareJournal != null,
                            onNameChange = onNameChange,
                            onDescriptionChange = onDescriptionChange,
                            onRequestDelete = onRequestDelete,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    standardContent = {
                        JournalSettingsStandardList(
                            uiState = uiState,
                            onShareJournal = onShareJournal,
                            onNameChange = onNameChange,
                            onDescriptionChange = onDescriptionChange,
                            onRequestDelete = onRequestDelete,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }

            if (showDeleteConfirmation) {
                HingeAwareDeleteConfirmation(
                    onDismissRequest = onDismissDeleteConfirmation,
                    onConfirmation = onConfirmDelete,
                )
            }
        }
    }
}

@Composable
private fun HingeAwareDeleteConfirmation(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    FoldableBookLayout(
        modifier = Modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f)),
            )
        },
        endPane = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                DeleteConfirmationCard(
                    onDismissRequest = onDismissRequest,
                    onConfirmation = onConfirmation,
                )
            }
        },
        standardContent = {
            DeleteConfirmationDialog(
                onDismissRequest = onDismissRequest,
                onConfirmation = onConfirmation,
            )
        },
    )
}

@Composable
private fun DeleteConfirmationCard(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.journal_delete_action),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text =
                    "Are you sure you want to delete this journal? " +
                        "This action cannot be undone and all entries in this journal will be permanently deleted.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(UiRes.string.common_cancel))
                }
                TextButton(
                    onClick = onConfirmation,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(UiRes.string.common_delete))
                }
            }
        }
    }
}
