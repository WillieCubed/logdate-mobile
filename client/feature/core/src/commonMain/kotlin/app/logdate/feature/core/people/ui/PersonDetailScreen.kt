@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Event
import app.logdate.ui.adaptive.FoldableBookLayout
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.people_title
import logdate.client.feature.core.generated.resources.person_aliases_label
import logdate.client.feature.core.generated.resources.person_detail_linked_entries
import logdate.client.feature.core.generated.resources.person_detail_linked_events
import logdate.client.feature.core.generated.resources.person_detail_load_failed
import logdate.client.feature.core.generated.resources.person_detail_loading
import logdate.client.feature.core.generated.resources.person_detail_no_linked_entries
import logdate.client.feature.core.generated.resources.person_detail_no_linked_events
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun PersonDetailScreen(
    personId: Uuid,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonDetailViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState().collectAsState()

    LaunchedEffect(personId) {
        viewModel.load(personId)
    }

    PersonDetailContent(
        uiState = uiState,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless person detail screen body.
 *
 * On a phone, tablet, or desktop this renders the existing single-pane list: a person's
 * identity (name, relationship, aliases, notes) followed by their linked entries and events,
 * all inside one [MaterialContainer]. On a foldable held open in book posture, the same
 * sections split across the hinge — identity on the start pane, linked activity on the end
 * pane — so the user reads the person and their history side by side.
 */
@Composable
fun PersonDetailContent(
    uiState: PersonDetailViewModel.UiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FoldableBookLayout(
        modifier = modifier.fillMaxSize(),
        minPaneWidth = 320.dp,
        startPane = {
            MaterialContainer(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PersonMetadataSection(uiState = uiState)
                }
            }
        },
        endPane = {
            MaterialContainer(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PersonLinkedSection(uiState = uiState)
                }
            }
        },
        standardContent = {
            SettingsScaffold(
                title = uiState.person?.name ?: stringResource(Res.string.people_title),
                onBack = onBack,
                modifier = modifier,
            ) {
                item {
                    MaterialContainer(
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            when {
                                uiState.isLoading -> {
                                    Text(stringResource(Res.string.person_detail_loading))
                                }

                                uiState.person == null -> {
                                    Text(
                                        text = stringResource(Res.string.person_detail_load_failed),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }

                                else -> {
                                    PersonMetadataSection(uiState = uiState)
                                    PersonLinkedSection(uiState = uiState)
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/**
 * Renders a person's identity: name, relationship, aliases, and free-form notes.
 *
 * Falls back to the loading and load-failed messages so a pane can stand on its own while the
 * profile is still resolving.
 */
@Composable
private fun PersonMetadataSection(uiState: PersonDetailViewModel.UiState) {
    when {
        uiState.isLoading -> {
            Text(stringResource(Res.string.person_detail_loading))
        }

        uiState.person == null -> {
            Text(
                text = stringResource(Res.string.person_detail_load_failed),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        else -> {
            val person = uiState.person
            Text(
                text = person.name,
                style = MaterialTheme.typography.headlineSmall,
            )
            person.relationshipLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (person.aliases.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.person_aliases_label, person.aliases.joinToString()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            person.notes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Renders the activity tied to a person: their linked journal entries and events.
 *
 * Shows nothing until the profile is loaded so the empty-state copy only appears once we know
 * the person genuinely has no linked activity.
 */
@Composable
private fun PersonLinkedSection(uiState: PersonDetailViewModel.UiState) {
    if (uiState.isLoading || uiState.person == null) {
        return
    }

    Text(
        text = stringResource(Res.string.person_detail_linked_entries),
        style = MaterialTheme.typography.titleMedium,
    )
    if (uiState.linkedEntries.isEmpty()) {
        Text(
            text = stringResource(Res.string.person_detail_no_linked_entries),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        uiState.linkedEntries.take(5).forEach { note ->
            Text(
                text = note.previewLabel(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Text(
        text = stringResource(Res.string.person_detail_linked_events),
        style = MaterialTheme.typography.titleMedium,
    )
    if (uiState.linkedEvents.isEmpty()) {
        Text(
            text = stringResource(Res.string.person_detail_no_linked_events),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        uiState.linkedEvents.take(5).forEach { event ->
            Text(
                text = event.previewLabel(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun JournalNote.previewLabel(): String =
    when (this) {
        is JournalNote.Text -> content.take(120)
        is JournalNote.Audio -> "Voice note"
        is JournalNote.Image -> caption.ifBlank { "Photo" }.take(120)
        is JournalNote.Video -> caption.ifBlank { "Video" }.take(120)
    }

private fun Event.previewLabel(): String = title
