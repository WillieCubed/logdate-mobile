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
import androidx.compose.ui.unit.dp
import app.logdate.ui.common.MaterialContainer
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

data class UserProfile(
    val name: String,
    val username: String, // @handle without the @
    val isEditable: Boolean = true,
    val isAuthenticated: Boolean = false
)

@Composable
fun ProfileSection(
    profile: UserProfile,
    onUpdateProfile: (displayName: String, username: String) -> Unit,
    modifier: Modifier = Modifier,
    isPreview: Boolean = false,
    onNavigateToAccount: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    MaterialContainer(modifier = modifier) {
        // Header
        UnsurfacedItem(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Manage your display name and username.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Profile Info Display
        val itemModifier = if (isPreview && onNavigateToAccount != null) {
            Modifier.clickable { onNavigateToAccount() }
        } else {
            Modifier
        }
        
        SurfaceItem(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = itemModifier
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = profile.name.ifEmpty { "No display name set" },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (profile.name.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (profile.username.isNotEmpty()) "@${profile.username}" else "No username set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (profile.username.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
                
                if (isPreview && onNavigateToAccount != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Go to account settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (profile.isEditable) {
                    IconButton(
                        onClick = { showEditDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
            }
        )
    }
}

@Composable
private fun EditProfileDialog(
    currentDisplayName: String,
    currentUsername: String,
    onDismiss: () -> Unit,
    onSave: (displayName: String, username: String) -> Unit
) {
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var username by remember { mutableStateOf(currentUsername) }
    var displayNameError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Validation functions
    fun validateDisplayName(name: String): String? {
        return when {
            name.isBlank() -> "Display name cannot be empty"
            name.length > 50 -> "Display name must be 50 characters or less"
            else -> null
        }
    }
    
    fun validateUsername(handle: String): String? {
        return when {
            handle.isBlank() -> "Username cannot be empty"
            handle.length < 3 -> "Username must be at least 3 characters"
            handle.length > 30 -> "Username must be 30 characters or less"
            !handle.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Username can only contain letters, numbers, and underscores"
            handle.startsWith("_") || handle.endsWith("_") -> "Username cannot start or end with underscore"
            else -> null
        }
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
            Text("Edit Profile")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { 
                        displayName = it
                        displayNameError = null
                    },
                    label = { Text("Display Name") },
                    placeholder = { Text("Your full name") },
                    supportingText = displayNameError?.let { { Text(it) } },
                    isError = displayNameError != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        // Remove @ if user types it
                        val cleanUsername = it.removePrefix("@")
                        username = cleanUsername
                        usernameError = null
                    },
                    label = { Text("Username") },
                    placeholder = { Text("username") },
                    supportingText = usernameError?.let { { Text(it) } } ?: {
                        Text("This will be your @${username.ifEmpty { "username" }}")
                    },
                    isError = usernameError != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (isValid()) {
                                onSave(displayName, username)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid()) {
                        onSave(displayName, username)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview
@Composable
private fun ProfileSectionPreview() {
    ProfileSection(
        profile = UserProfile(
            name = "John Doe",
            username = "johndoe",
            isAuthenticated = true
        ),
        onUpdateProfile = { _, _ -> }
    )
}

@Preview
@Composable
private fun ProfileSectionEmptyPreview() {
    ProfileSection(
        profile = UserProfile(
            name = "",
            username = "",
            isAuthenticated = false
        ),
        onUpdateProfile = { _, _ -> }
    )
}

@Preview
@Composable
private fun ProfileSectionPreviewWithNavigateOption() {
    ProfileSection(
        profile = UserProfile(
            name = "John Doe",
            username = "johndoe",
            isAuthenticated = true
        ),
        onUpdateProfile = { _, _ -> },
        isPreview = true,
        onNavigateToAccount = {}
    )
}

@Preview
@Composable
private fun EditProfileDialogPreview() {
    EditProfileDialog(
        currentDisplayName = "John Doe",
        currentUsername = "johndoe",
        onDismiss = {},
        onSave = { _, _ -> }
    )
}