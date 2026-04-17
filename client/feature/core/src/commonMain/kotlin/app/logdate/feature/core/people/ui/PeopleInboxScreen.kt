@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.people_inbox_empty_state
import logdate.client.feature.core.generated.resources.people_review_add_action
import logdate.client.feature.core.generated.resources.people_review_dismiss_action
import logdate.client.feature.core.generated.resources.people_review_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PeopleInboxScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeopleInboxViewModel = koinViewModel(),
) {
    val suggestions by viewModel.suggestions.collectAsState()

    SettingsScaffold(
        title = stringResource(Res.string.people_review_label),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (suggestions.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.people_inbox_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        } else {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    suggestions.forEach { suggestion ->
                        MaterialContainer {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                Text(
                                    text = suggestion.nameHint,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                suggestion.evidencePreview.forEach { preview ->
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    onClick = { viewModel.confirm(suggestion.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(Res.string.people_review_add_action))
                                }
                                TextButton(
                                    onClick = { viewModel.dismiss(suggestion.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(Res.string.people_review_dismiss_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
