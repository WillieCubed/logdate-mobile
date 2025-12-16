package app.logdate.feature.core.profile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.common.applyStandardContentWidth
import app.logdate.ui.theme.Spacing
import app.logdate.util.formatDateLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import app.logdate.shared.model.profile.LogDateProfile
import kotlinx.datetime.Instant
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Material 3 Expressive profile screen featuring a cookie-shaped profile photo,
 * headline-sized display name with inline editing, and organized information sections.
 * 
 * This screen follows Material Design 3 Expressive principles:
 * - Bold, expressive typography with headline sizing
 * - Dynamic colors and shapes for visual hierarchy
 * - Smooth motion and state transitions
 * - Container-based information architecture
 * - Accessible design patterns
 *
 * @param onBack Callback for when the user presses the back button
 * @param modifier Modifier to be applied to the screen
 * @param viewModel ViewModel for managing profile state
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    
    // Handle update success
    LaunchedEffect(uiState.updateState) {
        if (uiState.updateState is ProfileUpdateState.Success) {
            snackbarHostState.showSnackbar("Profile updated successfully")
        }
    }
    
    ProfileScreenContent(
        uiState = uiState,
        onBack = onBack,
        onStartEditingDisplayName = viewModel::startEditingDisplayName,
        onCancelEditing = viewModel::cancelEditing,
        onSaveDisplayName = viewModel::saveDisplayName,
        onRefresh = viewModel::refreshProfile,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreenContent(
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onStartEditingDisplayName: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    onRefresh: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    val profile = createProfileDisplayModel(
        localProfile = uiState.localProfile,
        account = uiState.account,
        userData = uiState.userData
    )
    
    Scaffold(
        modifier = modifier
            .applyScreenStyles()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh profile")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        
        // Show loading indicator
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        
        // Show update progress
        Column {
            AnimatedVisibility(
                visible = uiState.updateState is ProfileUpdateState.Updating,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .applyStandardContentWidth()
                    .verticalScroll(scrollState)
                    .padding(paddingValues)
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                // Profile header with cookie-shaped photo and headline display name
                ProfileHeader(
                    profile = profile,
                    editState = uiState.editState,
                    onStartEditingDisplayName = onStartEditingDisplayName,
                    onCancelEditing = onCancelEditing,
                    onSaveDisplayName = onSaveDisplayName,
                    modifier = Modifier.padding(vertical = Spacing.lg)
                )
                
                // Account Information Section (only show if user has cloud account)
                if (profile.hasCloudAccount) {
                    AccountInformationSection(
                        profile = profile,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                
                // Personal Information Section
                if (profile.birthday != null) {
                    PersonalInformationSection(
                        profile = profile,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: ProfileDisplayModel,
    editState: ProfileEditState,
    onStartEditingDisplayName: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Cookie-shaped profile photo (circular for now, could be enhanced to cookie shape)
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profile photo",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        // Display name with inline editing
        when (editState) {
            is ProfileEditState.DisplayName -> {
                // Edit mode
                var editedName by remember { mutableStateOf(editState.currentValue) }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        IconButton(onClick = onCancelEditing) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        IconButton(onClick = { onSaveDisplayName(editedName) }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                }
            }
            else -> {
                // Display mode with expressive headline typography
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                    
                    IconButton(
                        onClick = onStartEditingDisplayName,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit display name",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Username (only show if user has cloud account)
        profile.username?.let { username ->
            Text(
                text = "@$username",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable  
private fun AccountInformationSection(
    profile: ProfileDisplayModel,
    modifier: Modifier = Modifier
) {
    MaterialContainer(modifier = modifier) {
        SurfaceItem {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Account Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                profile.username?.let { username ->
                    ProfileInfoItem(
                        icon = Icons.Default.Person,
                        label = "Username",
                        value = "@$username"
                    )
                }
                
                profile.joinDate?.let { joinDate ->
                    ProfileInfoItem(
                        icon = Icons.Default.CalendarMonth,
                        label = "Member since",
                        value = formatDateLocalized(joinDate.toLocalDateTime(TimeZone.currentSystemDefault()).date)
                    )
                }
                
                // Authentication status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = if (profile.isAuthenticated) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Column {
                        Text(
                            text = "Authentication",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (profile.isAuthenticated) "Authenticated" else "Not authenticated",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (profile.isAuthenticated) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun PersonalInformationSection(
    profile: ProfileDisplayModel,
    modifier: Modifier = Modifier
) {
    MaterialContainer(modifier = modifier) {
        SurfaceItem {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = "Personal Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                profile.birthday?.let { birthday ->
                    ProfileInfoItem(
                        icon = Icons.Default.Cake,
                        label = "Birthday",
                        value = formatDateLocalized(birthday.toLocalDateTime(TimeZone.currentSystemDefault()).date)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview
@Composable
private fun ProfileScreenContentPreview() {
    ProfileScreenContent(
        uiState = ProfileUiState(
            localProfile = LogDateProfile(
                displayName = "John Doe",
                birthday = null
            ),
            account = null, // Would contain LogDateAccount in real usage
            userData = null,
            isLoading = false,
            editState = ProfileEditState.None,
            updateState = ProfileUpdateState.Idle
        ),
        onBack = {},
        onStartEditingDisplayName = {},
        onCancelEditing = {},
        onSaveDisplayName = {},
        onRefresh = {},
        snackbarHostState = SnackbarHostState()
    )
}