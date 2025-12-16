package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import app.logdate.ui.theme.Spacing
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.DefaultSettingsContentContainer
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.screen_title_settings
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main settings overview screen that displays navigation options to different settings sections.
 * This is the entry point for the settings flow and provides access to all specific settings areas.
 * 
 * The screen displays:
 * - A profile information section at the top
 * - Navigation options to Account & Profile settings
 * - Navigation options to Privacy & Security settings
 * - Navigation options to Data & Storage settings
 * - Navigation options to Devices settings
 * - Navigation options to Location settings
 * - Navigation to the Danger Zone for destructive actions
 *
 * This screen adapts to different layouts:
 * - In two-pane mode: Acts as the list pane with highlighting for selected items
 * - In single-pane mode: Standard navigation behavior
 *
 * @param onBack Callback for when the user presses the back button
 * @param onNavigateToProfile Callback for navigating to profile screen
 * @param onNavigateToAccount Callback for navigating to account settings
 * @param onNavigateToPrivacy Callback for navigating to privacy settings
 * @param onNavigateToData Callback for navigating to data settings
 * @param onNavigateToDevices Callback for navigating to devices settings
 * @param onNavigateToDangerZone Callback for navigating to danger zone settings
 * @param onNavigateToLocation Callback for navigating to location settings
 * @param selectedDetail Optional currently selected detail for highlighting in two-pane mode
 * @param isInTwoPaneMode Whether this screen is displayed in two-pane layout
 * @param viewModel ViewModel for the settings screen
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
    modifier: Modifier = Modifier,
    selectedDetail: String? = null,
    isInTwoPaneMode: Boolean = false,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    
    SettingsOverviewContent(
        onBack = onBack,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToAccount = onNavigateToAccount,
        onNavigateToPrivacy = onNavigateToPrivacy,
        onNavigateToData = onNavigateToData,
        onNavigateToDevices = onNavigateToDevices,
        onNavigateToDangerZone = onNavigateToDangerZone,
        onNavigateToLocation = onNavigateToLocation,
        userProfile = uiState.currentAccount.toUserProfile(),
        selectedDetail = selectedDetail,
        isInTwoPaneMode = isInTwoPaneMode,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsOverviewContent(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToDangerZone: () -> Unit,
    onNavigateToLocation: () -> Unit,
    userProfile: UserProfile,
    selectedDetail: String? = null,
    isInTwoPaneMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        modifier = modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isInTwoPaneMode) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.screen_title_settings)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Section title for two-pane mode
                if (isInTwoPaneMode) {
                    item {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
            // Profile preview (only show in single-pane mode or always show in two-pane)
            item {
                ProfileSection(
                    profile = userProfile,
                    onUpdateProfile = { _, _ -> /* Will be handled in profile screen */ },
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    isPreview = true,
                    onNavigateToProfile = onNavigateToProfile
                )
            }
            
            // Settings navigation options
            item {
                // Only show section title in single-pane mode
                if (!isInTwoPaneMode) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    )
                }
                
                MaterialContainer(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    // Profile
                    SurfaceItem {
                        SettingsNavigationItem(
                            title = "Profile",
                            description = "View and edit your profile information",
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                            onClick = onNavigateToProfile,
                            isSelected = selectedDetail == "profile"
                        )
                    }
                    
                    // Account Settings
                    SurfaceItem {
                        SettingsNavigationItem(
                            title = "Account",
                            description = "Manage your account settings and authentication",
                            icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                            onClick = onNavigateToAccount,
                            isSelected = selectedDetail == "account"
                        )
                    }
                    
                    // Devices
                    SurfaceItem {
                        SettingsNavigationItem(
                            title = "Devices",
                            description = "Manage your connected devices",
                            icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                            onClick = onNavigateToDevices,
                            isSelected = selectedDetail == "devices"
                        )
                    }
                    
                    // Privacy Settings
                    SurfaceItem {
                        SettingsNavigationItem(
                            title = "Privacy & Security",
                            description = "Control your privacy settings and app security",
                            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            onClick = onNavigateToPrivacy,
                            isSelected = selectedDetail == "privacy"
                        )
                    }
                    
                    // Location Settings
                    SurfaceItem {
                        SettingsNavigationItem(
                            title = "Location",
                            description = "Manage location tracking and history settings",
                            icon = { Icon(Icons.Default.ScreenshotMonitor, contentDescription = null) },
                            onClick = onNavigateToLocation,
                            isSelected = selectedDetail == "location"
                        )
                    }
                    
                    // Data Settings
                    SurfaceItem {
                        SettingsNavigationItem(
                            title = "Data & Storage",
                            description = "Manage your data usage and storage preferences",
                            icon = { Icon(Icons.Default.DataObject, contentDescription = null) },
                            onClick = onNavigateToData,
                            isSelected = selectedDetail == "data"
                        )
                    }
                    
                    // Danger Zone
                    SurfaceItem {
                        SettingsNavigationItem(
                            title = "Danger Zone",
                            description = "Reset app, delete data, and other destructive actions",
                            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = onNavigateToDangerZone,
                            isDangerous = true,
                            isSelected = selectedDetail == "danger"
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
    isSelected: Boolean = false
) {
    ListItem(
        headlineContent = { 
            Text(
                text = title,
                color = if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = { 
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        leadingContent = icon,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Navigate to $title"
            )
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .let { modifier ->
                if (isSelected) {
                    modifier.background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium
                    )
                } else {
                    modifier
                }
            }
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
        userProfile = UserProfile(
            name = "John Doe",
            username = "johndoe",
            isAuthenticated = true
        ),
        selectedDetail = null,
        isInTwoPaneMode = false
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
        userProfile = UserProfile(
            name = "",
            username = "",
            isAuthenticated = false
        ),
        selectedDetail = null,
        isInTwoPaneMode = false
    )
}