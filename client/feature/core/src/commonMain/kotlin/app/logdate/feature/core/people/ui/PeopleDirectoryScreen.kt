@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.people.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.people_directory_empty_state
import logdate.client.feature.core.generated.resources.people_directory_open_details
import logdate.client.feature.core.generated.resources.people_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun PeopleDirectoryScreen(
    onBack: () -> Unit,
    onOpenPerson: (Uuid) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeopleDirectoryViewModel = koinViewModel(),
) {
    val people by viewModel.people.collectAsState()

    SettingsScaffold(
        title = stringResource(Res.string.people_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (people.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.people_directory_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                )
            }
        } else {
            item {
                MaterialContainer(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    people.forEach { person ->
                        SettingsNavigationItem(
                            title = person.name,
                            description = stringResource(Res.string.people_directory_open_details),
                            icon = { Icon(Icons.Default.Person, contentDescription = null) },
                            onClick = { onOpenPerson(person.uid) },
                        )
                    }
                }
            }
        }
    }
}
