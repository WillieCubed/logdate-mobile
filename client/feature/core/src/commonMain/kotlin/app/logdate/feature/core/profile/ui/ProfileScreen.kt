@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.profile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.shared.model.profile.LogDateProfile
import app.logdate.ui.common.DefaultSettingsContentContainer
import app.logdate.ui.common.SettingsSection
import app.logdate.ui.common.applyScreenStyles
import app.logdate.ui.theme.Spacing
import app.logdate.util.formatDateLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_information
import logdate.client.feature.core.generated.resources.authentication
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.birthday
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.display_name
import logdate.client.feature.core.generated.resources.edit_display_name
import logdate.client.feature.core.generated.resources.no_display_name
import logdate.client.feature.core.generated.resources.personal_information
import logdate.client.feature.core.generated.resources.profile
import logdate.client.feature.core.generated.resources.profile_photo
import logdate.client.feature.core.generated.resources.profile_updated_successfully
import logdate.client.feature.core.generated.resources.save
import logdate.client.feature.core.generated.resources.username_handle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToBirthday: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val profileUpdatedMessage = stringResource(Res.string.profile_updated_successfully)

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.updateState) {
        if (uiState.updateState is ProfileUpdateState.Success) {
            snackbarHostState.showSnackbar(profileUpdatedMessage)
            viewModel.clearUpdateState()
        }
    }

    ProfileScreenContent(
        uiState = uiState,
        onBack = onBack,
        onNavigateToBirthday = onNavigateToBirthday,
        onStartEditingDisplayName = viewModel::startEditingDisplayName,
        onCancelEditing = viewModel::cancelEditing,
        onSaveDisplayName = viewModel::saveDisplayName,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent(
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onNavigateToBirthday: () -> Unit,
    onStartEditingDisplayName: () -> Unit,
    onCancelEditing: () -> Unit,
    onSaveDisplayName: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val profile =
        createProfileDisplayModel(
            localProfile = uiState.localProfile,
            account = uiState.account,
            userData = uiState.userData,
        )

    Scaffold(
        modifier =
            modifier
                .applyScreenStyles()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(Res.string.profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column {
            AnimatedVisibility(
                visible = uiState.updateState is ProfileUpdateState.Updating,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            DefaultSettingsContentContainer {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = paddingValues,
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // Profile header
                    item {
                        ProfileHeader(
                            profile = profile,
                            editState = uiState.editState,
                            onStartEditingDisplayName = onStartEditingDisplayName,
                            onCancelEditing = onCancelEditing,
                            onSaveDisplayName = onSaveDisplayName,
                            modifier =
                                Modifier
                                    .padding(horizontal = Spacing.lg)
                                    .padding(vertical = Spacing.lg),
                        )
                    }

                    // Personal Information Section
                    item {
                        SettingsSection(
                            title = stringResource(Res.string.personal_information),
                            modifier = Modifier.padding(horizontal = Spacing.lg),
                        ) {
                            val formattedBirthday =
                                if (profile.birthday == null || profile.birthday == Instant.DISTANT_PAST) {
                                    "Not set"
                                } else {
                                    formatDateLocalized(
                                        profile.birthday
                                            .toLocalDateTime(TimeZone.UTC)
                                            .date,
                                    )
                                }
                            ListItem(
                                headlineContent = { Text(stringResource(Res.string.birthday)) },
                                supportingContent = { Text(formattedBirthday) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.clickable { onNavigateToBirthday() },
                            )
                        }
                    }

                    // Account Information Section (only show if user has cloud account)
                    if (profile.hasCloudAccount) {
                        item {
                            SettingsSection(
                                title = stringResource(Res.string.account_information),
                                modifier = Modifier.padding(horizontal = Spacing.lg),
                            ) {
                                profile.username?.let { username ->
                                    ProfileInfoItem(
                                        icon = Icons.Default.Person,
                                        label = "Username",
                                        value = "@$username",
                                    )
                                }

                                profile.joinDate?.let { joinDate ->
                                    ProfileInfoItem(
                                        icon = Icons.Default.CalendarMonth,
                                        label = "Member since",
                                        value =
                                            formatDateLocalized(
                                                joinDate
                                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                                    .date,
                                            ),
                                    )
                                }

                                ListItem(
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Default.AccountCircle,
                                            contentDescription = null,
                                            tint =
                                                if (profile.isAuthenticated) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                },
                                        )
                                    },
                                    headlineContent = {
                                        Text(stringResource(Res.string.authentication))
                                    },
                                    supportingContent = {
                                        Text(
                                            text =
                                                if (profile.isAuthenticated) {
                                                    "Authenticated"
                                                } else {
                                                    "Not authenticated"
                                                },
                                            color =
                                                if (profile.isAuthenticated) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                },
                                        )
                                    },
                                )
                            }
                        }
                    }
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // Profile photo placeholder
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = stringResource(Res.string.profile_photo),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        // Display name with inline editing
        when (editState) {
            is ProfileEditState.DisplayName -> {
                var editedName by remember { mutableStateOf(editState.currentValue) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text(stringResource(Res.string.display_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        IconButton(onClick = onCancelEditing) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cancel))
                        }
                        IconButton(onClick = { onSaveDisplayName(editedName) }) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.save))
                        }
                    }
                }
            }
            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text =
                            if (profile.displayName.isEmpty() && !profile.isAuthenticated) {
                                stringResource(Res.string.no_display_name)
                            } else {
                                profile.displayName
                            },
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color =
                            if (profile.displayName.isEmpty() && !profile.isAuthenticated) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    IconButton(
                        onClick = onStartEditingDisplayName,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.edit_display_name),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Username (only show if user has cloud account)
        profile.username?.let { username ->
            Text(
                text = stringResource(Res.string.username_handle, username),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(label) },
        supportingContent = { Text(value) },
        modifier = modifier,
    )
}

@Preview
@Composable
fun ProfileScreenContentPreview() {
    ProfileScreenContent(
        uiState =
            ProfileUiState(
                localProfile =
                    LogDateProfile(
                        displayName = "John Doe",
                        birthday = null,
                    ),
                account = null,
                userData = null,
                isLoading = false,
                editState = ProfileEditState.None,
                updateState = ProfileUpdateState.Idle,
            ),
        onBack = {},
        onNavigateToBirthday = {},
        onStartEditingDisplayName = {},
        onCancelEditing = {},
        onSaveDisplayName = {},
        snackbarHostState = SnackbarHostState(),
    )
}
