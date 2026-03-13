@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_and_sign_in
import logdate.client.feature.core.generated.resources.account_settings_description
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.danger_zone
import logdate.client.feature.core.generated.resources.danger_zone_description
import logdate.client.feature.core.generated.resources.devices
import logdate.client.feature.core.generated.resources.devices_settings_description
import logdate.client.feature.core.generated.resources.edit_profile
import logdate.client.feature.core.generated.resources.export_and_import
import logdate.client.feature.core.generated.resources.export_and_import_description
import logdate.client.feature.core.generated.resources.location_settings
import logdate.client.feature.core.generated.resources.location_settings_description
import logdate.client.feature.core.generated.resources.logging_since
import logdate.client.feature.core.generated.resources.memories
import logdate.client.feature.core.generated.resources.memories_description
import logdate.client.feature.core.generated.resources.navigate_to_title
import logdate.client.feature.core.generated.resources.profile
import logdate.client.feature.core.generated.resources.profile_settings_description
import logdate.client.feature.core.generated.resources.screen_title_settings
import logdate.client.feature.core.generated.resources.settings_group_data_storage
import logdate.client.feature.core.generated.resources.settings_group_personal
import logdate.client.feature.core.generated.resources.settings_group_privacy_security
import logdate.client.feature.core.generated.resources.sync_and_backup
import logdate.client.feature.core.generated.resources.sync_and_backup_description
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

/**
 * Main settings overview screen that displays navigation options to different settings sections.
 * This is the entry point for the settings flow and serves as the list pane in list-detail layouts.
 *
 * Groups: Personal / Privacy & Security / Data & Storage
 */
@Composable
fun SettingsOverviewScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel = koinViewModel(),
) {
    val identity by viewModel.resolvedIdentity.collectAsState()

    SettingsOverviewContent(
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToDevices = onNavigateToDevices,
        onNavigateToDangerZone = onNavigateToDangerZone,
        onNavigateToLocation = onNavigateToLocation,
        onNavigateToMemories = onNavigateToMemories,
        onNavigateToSync = onNavigateToSync,
        onNavigateToExport = onNavigateToExport,
        userProfile =
            UserProfile(
                name = identity.displayName,
                username = identity.username ?: "",
                isAuthenticated = identity.isAuthenticated,
            ),
        onboardedDate = identity.onboardedDate ?: Instant.DISTANT_PAST,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverviewContent(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    userProfile: UserProfile,
    onboardedDate: Instant = Instant.DISTANT_PAST,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.screen_title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item {
                    SettingsIdentityCard(
                        userProfile = userProfile,
                        onboardedDate = onboardedDate,
                        onEditProfile = onNavigateToProfile,
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }

                // Personal group
                item {
                    SettingsSection(
                        title = stringResource(Res.string.settings_group_personal),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SettingsNavigationItem(
                            title = stringResource(Res.string.profile),
                            description = stringResource(Res.string.profile_settings_description),
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                            onClick = onNavigateToProfile,
                        )
                        SettingsNavigationItem(
                            title = stringResource(Res.string.memories),
                            description = stringResource(Res.string.memories_description),
                            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = onNavigateToMemories,
                        )
                    }
                }

                // Privacy & Security group
                item {
                    SettingsSection(
                        title = stringResource(Res.string.settings_group_privacy_security),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SettingsNavigationItem(
                            title = stringResource(Res.string.account_and_sign_in),
                            description = stringResource(Res.string.account_settings_description),
                            icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                            onClick = onNavigateToAccount,
                        )
                        SettingsNavigationItem(
                            title = stringResource(Res.string.devices),
                            description = stringResource(Res.string.devices_settings_description),
                            icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                            onClick = onNavigateToDevices,
                        )
                        SettingsNavigationItem(
                            title = stringResource(Res.string.location_settings),
                            description = stringResource(Res.string.location_settings_description),
                            icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            onClick = onNavigateToLocation,
                        )
                    }
                }

                // Data & Storage group
                item {
                    SettingsSection(
                        title = stringResource(Res.string.settings_group_data_storage),
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    ) {
                        SettingsNavigationItem(
                            title = stringResource(Res.string.sync_and_backup),
                            description = stringResource(Res.string.sync_and_backup_description),
                            icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                            onClick = onNavigateToSync,
                        )
                        SettingsNavigationItem(
                            title = stringResource(Res.string.export_and_import),
                            description = stringResource(Res.string.export_and_import_description),
                            icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = onNavigateToExport,
                        )
                        SettingsNavigationItem(
                            title = stringResource(Res.string.danger_zone),
                            description = stringResource(Res.string.danger_zone_description),
                            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = onNavigateToDangerZone,
                            isDangerous = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationItem(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    isDangerous: Boolean = false,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = icon,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription =
                    stringResource(
                        Res.string.navigate_to_title,
                        title,
                    ),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsIdentityCard(
    userProfile: UserProfile,
    onboardedDate: Instant,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = userProfile.name.ifEmpty { userProfile.username.ifEmpty { "You" } }
    val yearString =
        if (onboardedDate != Instant.DISTANT_PAST) {
            onboardedDate
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .year
                .toString()
        } else {
            null
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onEditProfile)
                .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // Avatar
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        // Name and subtitle
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (yearString != null) {
                Text(
                    text = stringResource(Res.string.logging_since, yearString),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }

        // Edit profile button
        ElevatedButton(
            onClick = onEditProfile,
            colors =
                ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Text(stringResource(Res.string.edit_profile))
        }
    }
}

@Preview
@Composable
private fun SettingsOverviewScreenPreview() {
    SettingsOverviewContent(
        onBack = {},
        onNavigateToProfile = {},
        onNavigateToAccount = {},
        onNavigateToDevices = {},
        onNavigateToDangerZone = {},
        onNavigateToLocation = {},
        onNavigateToMemories = {},
        onNavigateToSync = {},
        onNavigateToExport = {},
        userProfile =
            UserProfile(
                name = "John Doe",
                username = "johndoe",
                isAuthenticated = true,
            ),
    )
}

@Preview
@Composable
private fun SettingsOverviewScreenPreviewNotSignedIn() {
    SettingsOverviewContent(
        onBack = {},
        onNavigateToProfile = {},
        onNavigateToAccount = {},
        onNavigateToDevices = {},
        onNavigateToDangerZone = {},
        onNavigateToLocation = {},
        onNavigateToMemories = {},
        onNavigateToSync = {},
        onNavigateToExport = {},
        userProfile =
            UserProfile(
                name = "",
                username = "",
                isAuthenticated = false,
            ),
    )
}
