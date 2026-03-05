@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.theme.Spacing
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.cancel
import logdate.client.feature.core.generated.resources.display_name
import logdate.client.feature.core.generated.resources.edit_profile
import logdate.client.feature.core.generated.resources.edit_profile_2
import logdate.client.feature.core.generated.resources.go_to_account_settings
import logdate.client.feature.core.generated.resources.manage_your_display_name_and_username
import logdate.client.feature.core.generated.resources.profile
import logdate.client.feature.core.generated.resources.profile_username_hint
import logdate.client.feature.core.generated.resources.save
import logdate.client.feature.core.generated.resources.sign_in_to_logdate_cloud_settings_summary
import logdate.client.feature.core.generated.resources.username
import logdate.client.feature.core.generated.resources.username_2
import logdate.client.feature.core.generated.resources.your_full_name
import org.jetbrains.compose.resources.stringResource

data class UserProfile(
    val name: String,
    val username: String, // @handle without the @
    val isEditable: Boolean = true,
    val isAuthenticated: Boolean = false,
)

@Composable
fun ProfileSection(
    profile: UserProfile,
    onUpdateProfile: (displayName: String, username: String) -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
    onNavigateToAccount: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null,
) {
    var showEditDialog by remember { mutableStateOf(false) }

    MaterialContainer(modifier = modifier) {
        // Header
        UnsurfacedItem(
            modifier = Modifier.padding(Spacing.lg),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(Res.string.profile),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.manage_your_display_name_and_username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Profile Info Display
        if (!profile.isAuthenticated && isPreview) {
            // Show sign-in CTA when not authenticated in preview mode
            SurfaceItem(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier
                        .clickable { onNavigateToAccount?.invoke() }
                        .fillMaxWidth(),
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(Res.string.go_to_account_settings)) },
                    supportingContent = {
                        Text(
                            text = stringResource(Res.string.sign_in_to_logdate_cloud_settings_summary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.go_to_account_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        } else {
            val navigateAction = onNavigateToProfile ?: onNavigateToAccount
            val itemModifier =
                if (isPreview && navigateAction != null) {
                    Modifier.clickable { navigateAction() }
                } else {
                    Modifier
                }

            SurfaceItem(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = itemModifier,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                text = profile.name.ifEmpty { "No display name set" },
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                    if (profile.name.isNotEmpty()) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                            Text(
                                text = if (profile.username.isNotEmpty()) "@${profile.username}" else "No username set",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (profile.username.isNotEmpty()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    },
                            )
                        }
                    }

                    if (isPreview && navigateAction != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = stringResource(Res.string.go_to_account_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (profile.isEditable) {
                        IconButton(
                            onClick = { showEditDialog = true },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.edit_profile),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && !isPreview) {
        EditProfileDialog(
            currentDisplayName = profile.name,
            currentUsername = profile.username,
            onDismiss = { showEditDialog = false },
            onSave = { displayName, username ->
                onUpdateProfile(displayName, username)
                showEditDialog = false
            },
        )
    }
}

@Composable
private fun EditProfileDialog(
    currentDisplayName: String,
    currentUsername: String,
    onDismiss: () -> Unit,
    onSave: (displayName: String, username: String) -> Unit,
) {
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var username by remember { mutableStateOf(currentUsername) }
    var displayNameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current

    // Validation functions
    fun validateDisplayName(name: String): String? =
        when {
            name.isBlank() -> "Display name cannot be empty"
            name.length > 50 -> "Display name must be 50 characters or less"
            else -> null
        }

    fun validateUsername(handle: String): String? =
        when {
            handle.isBlank() -> "Username cannot be empty"
            handle.length < 3 -> "Username must be at least 3 characters"
            handle.length > 30 -> "Username must be 30 characters or less"
            !handle.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
            handle.startsWith("_") || handle.endsWith("_") -> "Username cannot start or end with underscore"
            else -> null
        }

    fun isValid(): Boolean {
        val nameError = validateDisplayName(displayName)
        val handleError = validateUsername(username)
        displayNameError = nameError
        usernameError = handleError
        return nameError == null && handleError == null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.edit_profile_2))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        displayName = it
                        displayNameError = null
                    },
                    label = { Text(stringResource(Res.string.display_name)) },
                    placeholder = { Text(stringResource(Res.string.your_full_name)) },
                    supportingText = displayNameError?.let { { Text(it) } },
                    isError = displayNameError != null,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        // Remove @ if user types it
                        val cleanUsername = it.removePrefix("@")
                        username = cleanUsername
                        usernameError = null
                    },
                    label = { Text(stringResource(Res.string.username)) },
                    placeholder = { Text(stringResource(Res.string.username_2)) },
                    supportingText =
                        usernameError?.let { { Text(it) } } ?: {
                            Text(
                                stringResource(
                                    Res.string.profile_username_hint,
                                    username.ifEmpty { "username" },
                                ),
                            )
                        },
                    isError = usernameError != null,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (isValid()) {
                                    onSave(displayName, username)
                                }
                            },
                        ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid()) {
                        onSave(displayName, username)
                    }
                },
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Preview
@Composable
private fun ProfileSectionPreview() {
    ProfileSection(
        profile =
            UserProfile(
                name = "John Doe",
                username = "johndoe",
                isAuthenticated = true,
            ),
        onUpdateProfile = { _, _ -> },
    )
}

@Preview
@Composable
private fun ProfileSectionEmptyPreview() {
    ProfileSection(
        profile =
            UserProfile(
                name = "",
                username = "",
                isAuthenticated = false,
            ),
        onUpdateProfile = { _, _ -> },
    )
}

@Preview
@Composable
private fun ProfileSectionPreviewWithNavigateOption() {
    ProfileSection(
        profile =
            UserProfile(
                name = "John Doe",
                username = "johndoe",
                isAuthenticated = true,
            ),
        onUpdateProfile = { _, _ -> },
        isPreview = true,
        onNavigateToAccount = {},
    )
}

@Preview
@Composable
private fun EditProfileDialogPreview() {
    EditProfileDialog(
        currentDisplayName = "John Doe",
        currentUsername = "johndoe",
        onDismiss = {},
        onSave = { _, _ -> },
    )
}
