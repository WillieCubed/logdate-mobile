@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.navigate_to_title
import logdate.client.feature.core.generated.resources.screen_title_settings
import logdate.client.feature.core.generated.resources.settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main settings overview screen that displays navigation options to different settings sections.
 * This is the entry point for the settings flow and serves as the list pane in list-detail layouts.
 */
@Composable
fun SettingsOverviewScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountSettingsViewModel = koinViewModel(),
) {
    val accountState by viewModel.state.collectAsState()

    SettingsOverviewContent(
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToData = onNavigateToData,
        onNavigateToDevices = onNavigateToDevices,
        onNavigateToDangerZone = onNavigateToDangerZone,
        onNavigateToLocation = onNavigateToLocation,
        onNavigateToAdvanced = onNavigateToAdvanced,
        userProfile = accountState.currentAccount.toUserProfile(),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverviewContent(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    onNavigateToAdvanced: () -> Unit,
    userProfile: UserProfile,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
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
                    ProfileSection(
                        profile = userProfile,
                        onUpdateProfile = { _, _ -> },
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                        isPreview = true,
                        onNavigateToProfile = onNavigateToProfile,
                    )
                }

                item {
                    Text(
                        text = stringResource(Res.string.settings),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )

                    MaterialContainer(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Profile",
                                description = "View and edit your profile information",
                                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                onClick = onNavigateToProfile,
                            )
                        }

                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Account",
                                description = "Manage your account settings and authentication",
                                icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                onClick = onNavigateToAccount,
                            )
                        }

                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Devices",
                                description = "Manage your connected devices",
                                icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                onClick = onNavigateToDevices,
                            )
                        }

                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Privacy & Security",
                                description = "Control your privacy settings and app security",
                                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = onNavigateToPrivacy,
                            )
                        }

                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Location",
                                description = "Manage location tracking and history settings",
                                icon = { Icon(Icons.Default.ScreenshotMonitor, contentDescription = null) },
                                onClick = onNavigateToLocation,
                            )
                        }

                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Data & Storage",
                                description = "Manage your data usage and storage preferences",
                                icon = { Icon(Icons.Default.DataObject, contentDescription = null) },
                                onClick = onNavigateToData,
                            )
                        }

                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Advanced",
                                description = "Server configuration and developer options",
                                icon = { Icon(Icons.Default.DeveloperMode, contentDescription = null) },
                                onClick = onNavigateToAdvanced,
                            )
                        }

                        SurfaceItem {
                            SettingsNavigationItem(
                                title = "Danger Zone",
                                description = "Reset app, delete data, and other destructive actions",
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

@Preview
@Composable
private fun SettingsOverviewScreenPreview() {
    SettingsOverviewContent(
        onBack = {},
        onNavigateToProfile = {},
        onNavigateToAccount = {},
        onNavigateToPrivacy = {},
        onNavigateToData = {},
        onNavigateToDevices = {},
        onNavigateToDangerZone = {},
        onNavigateToLocation = {},
        onNavigateToAdvanced = {},
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
        onNavigateToPrivacy = {},
        onNavigateToData = {},
        onNavigateToDevices = {},
        onNavigateToDangerZone = {},
        onNavigateToLocation = {},
        onNavigateToAdvanced = {},
        userProfile =
            UserProfile(
                name = "",
                username = "",
                isAuthenticated = false,
            ),
    )
}
