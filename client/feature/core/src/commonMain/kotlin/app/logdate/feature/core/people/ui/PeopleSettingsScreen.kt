@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.logdate.client.permissions.ContactsPermissionState
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.SettingsNavigationItem
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.ToggleSettingsItem
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.people_browse_description
import logdate.client.feature.core.generated.resources.people_browse_label
import logdate.client.feature.core.generated.resources.people_choose_contacts
import logdate.client.feature.core.generated.resources.people_directory_empty_state
import logdate.client.feature.core.generated.resources.people_disabled_notice
import logdate.client.feature.core.generated.resources.people_enable_description
import logdate.client.feature.core.generated.resources.people_enable_label
import logdate.client.feature.core.generated.resources.people_find_more
import logdate.client.feature.core.generated.resources.people_get_started
import logdate.client.feature.core.generated.resources.people_import_all_contacts
import logdate.client.feature.core.generated.resources.people_importing_contacts
import logdate.client.feature.core.generated.resources.people_review_description
import logdate.client.feature.core.generated.resources.people_review_label
import logdate.client.feature.core.generated.resources.people_settings_description
import logdate.client.feature.core.generated.resources.people_setup_description
import logdate.client.feature.core.generated.resources.people_setup_full_description
import logdate.client.feature.core.generated.resources.people_setup_selected_description
import logdate.client.feature.core.generated.resources.people_setup_title
import logdate.client.feature.core.generated.resources.people_title
import logdate.client.ui.generated.resources.common_dismiss
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import logdate.client.ui.generated.resources.Res as UiRes

@Composable
fun PeopleSettingsScreen(
    onBack: () -> Unit,
    onBrowsePeople: () -> Unit,
    onOpenReviewInbox: () -> Unit,
    contactsPermissionState: ContactsPermissionState,
    onImportSelectedContacts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeopleSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isPeopleEnabled) {
        if (uiState.isPeopleEnabled) {
            viewModel.refreshPeople()
        }
    }

    PeopleSettingsContent(
        uiState = uiState,
        onBack = onBack,
        onBrowsePeople = onBrowsePeople,
        onOpenReviewInbox = onOpenReviewInbox,
        contactsPermissionState = contactsPermissionState,
        onPeopleEnabledChanged = viewModel::setPeopleEnabled,
        onImportAllContacts = {
            if (contactsPermissionState.hasPermission) {
                viewModel.importAllContacts()
            } else {
                contactsPermissionState.requestPermission()
            }
        },
        onImportSelectedContacts = onImportSelectedContacts,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleSettingsContent(
    uiState: PeopleSettingsViewModel.UiState,
    onBack: () -> Unit,
    onBrowsePeople: () -> Unit,
    onOpenReviewInbox: () -> Unit,
    contactsPermissionState: ContactsPermissionState,
    onPeopleEnabledChanged: (Boolean) -> Unit,
    onImportAllContacts: () -> Unit,
    onImportSelectedContacts: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSetupSheet by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(Res.string.people_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            Text(
                text = stringResource(Res.string.people_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }

        item {
            MaterialContainer(
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                ToggleSettingsItem(
                    title = stringResource(Res.string.people_enable_label),
                    description = stringResource(Res.string.people_enable_description),
                    checked = uiState.isPeopleEnabled,
                    onCheckedChange = onPeopleEnabledChanged,
                )
            }
        }

        item {
            MaterialContainer(
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                SettingsNavigationItem(
                    title = stringResource(Res.string.people_browse_label),
                    description =
                        if (uiState.totalPeopleCount == 0) {
                            stringResource(Res.string.people_directory_empty_state)
                        } else {
                            stringResource(Res.string.people_browse_description, uiState.totalPeopleCount)
                        },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    onClick = onBrowsePeople,
                    enabled = uiState.isPeopleEnabled && uiState.totalPeopleCount > 0,
                )
            }
        }

        if (uiState.pendingReviewCount > 0) {
            item {
                MaterialContainer(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                ) {
                    SettingsNavigationItem(
                        title = stringResource(Res.string.people_review_label),
                        description = stringResource(Res.string.people_review_description, uiState.pendingReviewCount),
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        onClick = onOpenReviewInbox,
                        enabled = uiState.isPeopleEnabled,
                    )
                }
            }
        }

        item {
            MaterialContainer(
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        text = stringResource(Res.string.people_setup_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text =
                            if (uiState.isPeopleEnabled) {
                                stringResource(Res.string.people_setup_description)
                            } else {
                                stringResource(Res.string.people_disabled_notice)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showSetupSheet = true },
                        enabled = uiState.isPeopleEnabled && !uiState.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Text(
                            text =
                                if (uiState.totalPeopleCount == 0) {
                                    stringResource(Res.string.people_get_started)
                                } else {
                                    stringResource(Res.string.people_find_more)
                                },
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                    if (uiState.isImporting) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text(
                                text = stringResource(Res.string.people_importing_contacts),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        uiState.notice?.let { message ->
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
                        Text(
                            text = peopleSettingsNoticeText(message),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = onDismissMessage) {
                            Text(stringResource(UiRes.string.common_dismiss))
                        }
                    }
                }
            }
        }
    }

    if (showSetupSheet) {
        ModalBottomSheet(onDismissRequest = { showSetupSheet = false }) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.people_setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(Res.string.people_setup_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        showSetupSheet = false
                        onImportAllContacts()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.people_import_all_contacts))
                }
                Text(
                    text = stringResource(Res.string.people_setup_full_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.supportsSelectedContactsPicker) {
                    OutlinedButton(
                        onClick = {
                            showSetupSheet = false
                            onImportSelectedContacts()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.people_choose_contacts))
                    }
                    Text(
                        text = stringResource(Res.string.people_setup_selected_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!contactsPermissionState.hasPermission && !uiState.supportsSelectedContactsPicker) {
                    Text(
                        text = stringResource(Res.string.people_setup_full_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
