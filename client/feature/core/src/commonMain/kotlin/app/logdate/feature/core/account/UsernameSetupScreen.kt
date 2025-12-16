package app.logdate.feature.core.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun UsernameSetupScreen(
    username: String,
    onUsernameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    usernameAvailability: UsernameAvailability = UsernameAvailability.Unknown,
    isValid: Boolean = true,
    modifier: Modifier = Modifier
) {
    UsernameSetupContent(
        username = username,
        onUsernameChange = onUsernameChange,
        onContinue = onContinue,
        onBack = onBack,
        usernameAvailability = usernameAvailability,
        isValid = isValid,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsernameSetupContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    usernameAvailability: UsernameAvailability,
    isValid: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back"
                    )
                }
                
                LinearProgressIndicator(
                    progress = { 0.66f }, // Step 2 of 3
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.md),
                )
                
                Text(
                    text = "2 of 3",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.lg))
            
            // Title and description
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Choose your username",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Your unique address on the LogDate network. This is how others will find and mention you.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
            }
            
            // Username input
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    placeholder = { Text("your_username") },
                    prefix = { Text("@") },
                    suffix = { Text("@logdate.app") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AlternateEmail,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        when (usernameAvailability) {
                            UsernameAvailability.Checking -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            UsernameAvailability.Available -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Username available",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            UsernameAvailability.Taken -> {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Username taken",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            UsernameAvailability.Error -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error checking username",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            UsernameAvailability.Unknown -> null
                        }
                    },
                    supportingText = {
                        when (usernameAvailability) {
                            UsernameAvailability.Available -> Text(
                                text = "✓ Username is available",
                                color = MaterialTheme.colorScheme.primary
                            )
                            UsernameAvailability.Taken -> Text(
                                text = "Username is already taken",
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> Text("Your unique address will be @$username@logdate.app")
                        }
                    },
                    isError = usernameAvailability == UsernameAvailability.Taken,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.None
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { 
                            focusManager.clearFocus()
                            if (isValid && username.isNotBlank() && usernameAvailability == UsernameAvailability.Available) {
                                onContinue()
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                
                // ActivityPub/Fediverse explanation
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Connected to the Fediverse",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        
                        Text(
                            text = "LogDate uses ActivityPub, the same technology that powers Mastodon, Pixelfed, and other social networks. This means you can interact with a global community while keeping control of your data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.padding(top = Spacing.xs)
                        ) {
                            Chip(
                                text = "Decentralized",
                                icon = Icons.Default.Hub
                            )
                            Chip(
                                text = "Open Source",
                                icon = Icons.Default.Code
                            )
                        }
                    }
                }
                
                // Username guidelines
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            text = "💡 Username tips:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Use only letters, numbers, and underscores\n• Keep it memorable and easy to share\n• 3-30 characters long\n• Can't be changed later",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Continue button
        Button(
            onClick = onContinue,
            enabled = isValid && username.isNotBlank() && usernameAvailability == UsernameAvailability.Available,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun Chip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview
@Composable
private fun UsernameSetupScreenPreview() {
    MaterialTheme {
        Surface {
            UsernameSetupContent(
                username = "alex_j",
                onUsernameChange = {},
                onContinue = {},
                onBack = {},
                usernameAvailability = UsernameAvailability.Available,
                isValid = true
            )
        }
    }
}

@Preview
@Composable
private fun UsernameSetupScreenTakenPreview() {
    MaterialTheme {
        Surface {
            UsernameSetupContent(
                username = "admin",
                onUsernameChange = {},
                onContinue = {},
                onBack = {},
                usernameAvailability = UsernameAvailability.Taken,
                isValid = false
            )
        }
    }
}