@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.client.repository.journals.JournalNote
import app.logdate.shared.model.Event
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
                            val person = requireNotNull(uiState.person)
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
                    }
                }
            }
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
