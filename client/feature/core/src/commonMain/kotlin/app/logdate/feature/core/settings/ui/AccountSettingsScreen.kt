package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import app.logdate.feature.core.settings.ui.components.formatDateLocalized
import app.logdate.shared.model.user.UserData
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account management settings screen.
 *
 * This screen automatically adapts to different screen sizes:
 * - Large screens: Acts as a detail pane with minimal header (when in two-pane layout)
 * - Small screens: Standard screen with back navigation
 *
 * @param onBack Callback for when the user presses the back button
 * @param onNavigateToCloudAccountCreation Callback for creating a cloud account
 * @param onNavigateToBirthdaySettings Callback for navigating to the full-screen birthday selector
 * @param viewModel ViewModel for the settings
 */
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit,
    onNavigateToBirthdaySettings: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    // Detect if we're in a large screen layout where this might be a detail pane
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isPotentialDetailPane = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val uiState by viewModel.uiState.collectAsState()
    val birthdayUpdateState by viewModel.birthdayUpdateState.collectAsState()
    
    AccountSettingsContent(
        onBack = onBack,
        onNavigateToCloudAccountCreation = onNavigateToCloudAccountCreation,
        onNavigateToBirthdaySettings = onNavigateToBirthdaySettings,
        userProfile = uiState.currentAccount.toUserProfile(),
        passkeys = uiState.currentAccount.toPasskeyInfoList(),
        userData = uiState.userData,
        onUpdateProfile = { displayName, username -> 
            // TODO: Implement profile update
        },
        onRevokePasskey = { /* TODO: Implement */ },
        birthdayUpdateState = birthdayUpdateState,
        isPotentialDetailPane = isPotentialDetailPane
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSettingsContent(
    onBack: () -> Unit,
    onNavigateToCloudAccountCreation: () -> Unit,
    onNavigateToBirthdaySettings: () -> Unit,
    userProfile: UserProfile,
    passkeys: List<PasskeyInfo>,
    userData: UserData,
    onUpdateProfile: (displayName: String, username: String) -> Unit,
    onRevokePasskey: (PasskeyInfo) -> Unit,
    birthdayUpdateState: BirthdayUpdateState,
    isPotentialDetailPane: Boolean = false
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // State for profile edit fields
    var displayName by remember { mutableStateOf(userProfile.name) }
    var username by remember { mutableStateOf(userProfile.username) }
    
    // Handle birthday update state changes
    LaunchedEffect(birthdayUpdateState) {
        when (birthdayUpdateState) {
            is BirthdayUpdateState.Success -> {
                snackbarHostState.showSnackbar("Birthday updated successfully")
            }
            is BirthdayUpdateState.Error -> {
                snackbarHostState.showSnackbar(
                    "Failed to update birthday: ${birthdayUpdateState.message}"
                )
            }
            else -> { /* No action needed */ }
        }
    }
    
    Scaffold(
        modifier = Modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // Only show top bar with back button in single-pane mode
            if (!isPotentialDetailPane) {
                TopAppBar(
                    title = { Text("Account & Profile") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        DefaultSettingsContentContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Section title for two-pane mode
                if (isPotentialDetailPane) {
                    item {
                        Text(
                            text = "Account & Profile",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    }
                }
                
                // Profile edit section
                item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "Profile Information",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    TextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    TextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Button(
                        onClick = { onUpdateProfile(displayName, username) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update Profile")
                    }
                }
            }
            
            // Passkeys section
            item {
                PasskeysInfoSection(
                    passkeys = passkeys,
                    onCreatePasskey = onNavigateToCloudAccountCreation,
                    onRevokePasskey = onRevokePasskey,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }
            
            // Personal information
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Personal Information",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Birthday selector in MaterialContainer - now navigates to full screen
                    MaterialContainer {
                        SurfaceItem {
                            ListItem(
                                headlineContent = { Text("Birthday") },
                                supportingContent = { 
                                    val formattedBirthday = if (userData.birthday == Instant.DISTANT_PAST) {
                                        "Set your birthday!"
                                    } else {
                                        val localDate = userData.birthday.toLocalDateTime(TimeZone.currentSystemDefault()).date
                                        formatDateLocalized(localDate)
                                    }
                                    Text(formattedBirthday) 
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.clickable { onNavigateToBirthdaySettings() }
                            )
                        }
                    }
                }
            }
            
            // Account preferences
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = "Account Preferences",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    MaterialContainer {
                        SurfaceItem {
                            ListItem(
                                headlineContent = { Text("Email Notifications") },
                                supportingContent = { Text("Configure email notification preferences") },
                                trailingContent = {
                                    Button(onClick = { /* TODO: Implement */ }) {
                                        Text("Configure")
                                    }
                                }
                            )
                        }
                        
                        SurfaceItem {
                            ListItem(
                                headlineContent = { Text("Account Activity") },
                                supportingContent = { Text("View your recent account activity") },
                                trailingContent = {
                                    Button(onClick = { /* TODO: Implement */ }) {
                                        Text("View")
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Preview
@Composable
private fun AccountSettingsScreenPreview() {
    AccountSettingsContent(
        onBack = {},
        onNavigateToCloudAccountCreation = {},
        onNavigateToBirthdaySettings = {},
        userProfile = UserProfile(
            name = "John Doe",
            username = "johndoe",
            isAuthenticated = true
        ),
        passkeys = listOf(
            PasskeyInfo(
                id = "passkey1",
                name = "Preview Passkey",
                device = "Demo Device",
                createdAt = "Jan 1, 2023",
                lastUsed = Clock.System.now()
            )
        ),
        userData = UserData(
            birthday = Clock.System.now(),
            isOnboarded = true,
            onboardedDate = Clock.System.now()
        ),
        onUpdateProfile = { _, _ -> },
        onRevokePasskey = {},
        birthdayUpdateState = BirthdayUpdateState.Idle
    )
}