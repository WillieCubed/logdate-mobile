@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.SettingsScaffold
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_and_sign_in
import logdate.client.feature.core.generated.resources.account_settings_description
import logdate.client.feature.core.generated.resources.create_account
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
import logdate.client.feature.core.generated.resources.notifications_settings
import logdate.client.feature.core.generated.resources.notifications_settings_description
import logdate.client.feature.core.generated.resources.privacy_and_security
import logdate.client.feature.core.generated.resources.privacy_security_description
import logdate.client.feature.core.generated.resources.profile
import logdate.client.feature.core.generated.resources.profile_settings_description
import logdate.client.feature.core.generated.resources.reset
import logdate.client.feature.core.generated.resources.reset_description
import logdate.client.feature.core.generated.resources.screen_title_settings
import logdate.client.feature.core.generated.resources.settings_group_data_storage
import logdate.client.feature.core.generated.resources.settings_group_personal
import logdate.client.feature.core.generated.resources.settings_group_privacy_security
import logdate.client.feature.core.generated.resources.sign_in
import logdate.client.feature.core.generated.resources.streak_day_count
import logdate.client.feature.core.generated.resources.streaks
import logdate.client.feature.core.generated.resources.streaks_description
import logdate.client.feature.core.generated.resources.sync_and_backup
import logdate.client.feature.core.generated.resources.sync_and_backup_description
import logdate.client.feature.core.generated.resources.sync_promotion_description
import logdate.client.feature.core.generated.resources.sync_promotion_title
import logdate.client.feature.core.generated.resources.timeline_settings
import logdate.client.feature.core.generated.resources.timeline_settings_description
import logdate.client.feature.core.generated.resources.watch_settings
import logdate.client.feature.core.generated.resources.watch_settings_description
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
    onNavigateToWatch: () -> Unit,
    onNavigateToReset: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToLibrarySettings: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToStreaks: () -> Unit = {},
    onNavigateToTimeline: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel = koinViewModel(),
) {
    val identity by viewModel.resolvedIdentity.collectAsState()
    val streakData by viewModel.streakData.collectAsState()

    SettingsOverviewContent(
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToDevices = onNavigateToDevices,
        onNavigateToWatch = onNavigateToWatch,
        onNavigateToReset = onNavigateToReset,
        onNavigateToLocation = onNavigateToLocation,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToLibrarySettings = onNavigateToLibrarySettings,
        onNavigateToMemories = onNavigateToMemories,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToStreaks = onNavigateToStreaks,
        onNavigateToTimeline = onNavigateToTimeline,
        onNavigateToSync = onNavigateToSync,
        onNavigateToExport = onNavigateToExport,
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToSignIn = onNavigateToSignIn,
        userProfile =
            UserProfile(
                name = identity.displayName,
                username = identity.username ?: "",
                isAuthenticated = identity.isAuthenticated,
            ),
        onboardedDate = identity.onboardedDate ?: Instant.DISTANT_PAST,
        streakCount = if (streakData.isEnabled) streakData.currentStreak else null,
        modifier = modifier,
    )
}

@Composable
fun SettingsOverviewContent(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToWatch: () -> Unit = {},
    onNavigateToReset: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToNotifications: (() -> Unit)? = null,
    onNavigateToStreaks: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToSync: () -> Unit,
    onNavigateToExport: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToLibrarySettings: () -> Unit = {},
    userProfile: UserProfile,
    onboardedDate: Instant = Instant.DISTANT_PAST,
    streakCount: Int? = null,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(Res.string.screen_title_settings),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            SettingsIdentityCard(
                userProfile = userProfile,
                onboardedDate = onboardedDate,
                streakCount = streakCount,
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
                SettingsNavigationItem(
                    title = stringResource(Res.string.timeline_settings),
                    description = stringResource(Res.string.timeline_settings_description),
                    icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                    onClick = onNavigateToTimeline,
                )
                SettingsNavigationItem(
                    title = stringResource(Res.string.streaks),
                    description = stringResource(Res.string.streaks_description),
                    icon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = null) },
                    onClick = onNavigateToStreaks,
                )
                SettingsNavigationItem(
                    title = "Your library",
                    description = "Browse and manage your photos and videos",
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    onClick = onNavigateToLibrarySettings,
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
                    title = stringResource(Res.string.privacy_and_security),
                    description = stringResource(Res.string.privacy_security_description),
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    onClick = onNavigateToPrivacy,
                )
                if (userProfile.isAuthenticated) {
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
                }
                SettingsNavigationItem(
                    title = stringResource(Res.string.location_settings),
                    description = stringResource(Res.string.location_settings_description),
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    onClick = onNavigateToLocation,
                )
                SettingsNavigationItem(
                    title = stringResource(Res.string.watch_settings),
                    description = stringResource(Res.string.watch_settings_description),
                    icon = { Icon(Icons.Default.Watch, contentDescription = null) },
                    onClick = onNavigateToWatch,
                )
                onNavigateToNotifications?.let { navigateToNotifications ->
                    SettingsNavigationItem(
                        title = stringResource(Res.string.notifications_settings),
                        description = stringResource(Res.string.notifications_settings_description),
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        onClick = navigateToNotifications,
                    )
                }
            }
        }

        // Data & Storage group
        item {
            SettingsSection(
                title = stringResource(Res.string.settings_group_data_storage),
                modifier = Modifier.padding(horizontal = Spacing.lg),
            ) {
                if (userProfile.isAuthenticated) {
                    SettingsNavigationItem(
                        title = stringResource(Res.string.sync_and_backup),
                        description = stringResource(Res.string.sync_and_backup_description),
                        icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                        onClick = onNavigateToSync,
                    )
                } else {
                    SyncPromotionCard(
                        onCreateAccount = onNavigateToCloudAccountCreation,
                        onSignIn = onNavigateToSignIn,
                        onNavigateToSync = onNavigateToSync,
                    )
                }
                SettingsNavigationItem(
                    title = stringResource(Res.string.export_and_import),
                    description = stringResource(Res.string.export_and_import_description),
                    icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                    onClick = onNavigateToExport,
                )
                SettingsNavigationItem(
                    title = stringResource(Res.string.reset),
                    description = stringResource(Res.string.reset_description),
                    icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                    onClick = onNavigateToReset,
                )
            }
        }
    }
}

@Composable
private fun SyncPromotionCard(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onNavigateToSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onNavigateToSync)
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = stringResource(Res.string.sync_promotion_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = stringResource(Res.string.sync_promotion_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(onClick = onCreateAccount) {
                Text(stringResource(Res.string.create_account))
            }
            OutlinedButton(onClick = onSignIn) {
                Text(stringResource(Res.string.sign_in))
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
) {
    ListItem(
        headlineContent = {
            Text(text = title)
        },
        supportingContent = {
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
    streakCount: Int? = null,
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
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (yearString != null) {
                    Text(
                        text = stringResource(Res.string.logging_since, yearString),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                if (streakCount != null && streakCount > 0) {
                    Row(
                        modifier =
                            Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(Res.string.streak_day_count, streakCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        FilledTonalButton(
            onClick = onEditProfile,
            colors =
                ButtonDefaults.filledTonalButtonColors(
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
        onNavigateToReset = {},
        onNavigateToLocation = {},
        onNavigateToPrivacy = {},
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
        onNavigateToReset = {},
        onNavigateToLocation = {},
        onNavigateToPrivacy = {},
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
