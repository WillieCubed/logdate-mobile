package app.logdate.feature.core.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.logdate.ui.theme.Spacing
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun CloudAccountSignInScreen(
    onSignIn: (username: String, serverUrl: String) -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsOfService: () -> Unit,
    onBack: () -> Unit,
    isSigningIn: Boolean = false,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var serverDomain by remember { mutableStateOf("logdate.app") }
    var isServerEditable by remember { mutableStateOf(false) }
    var showRecoveryPopup by remember { mutableStateOf(false) }
    var hasTriggeredAutoPasskey by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val serverFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(isServerEditable) {
        if (isServerEditable) {
            serverFocusRequester.requestFocus()
        }
    }
    
    // Auto-trigger passkey dialog when screen loads with a default username
    LaunchedEffect(Unit) {
        if (!hasTriggeredAutoPasskey) {
            hasTriggeredAutoPasskey = true
            // Trigger with a temporary username or empty to show universal passkey picker
            onSignIn("", serverDomain)
        }
    }

    CloudAccountSignInContent(
        username = username,
        onUsernameChange = { username = it },
        serverDomain = serverDomain,
        onServerDomainChange = { serverDomain = it },
        isServerEditable = isServerEditable,
        onServerDomainDoubleClick = { isServerEditable = true },
        onServerFocusLost = { isServerEditable = false },
        serverFocusRequester = serverFocusRequester,
        onSignIn = { onSignIn(username, serverDomain) },
        onAccountRecovery = { showRecoveryPopup = true },
        onPrivacyPolicy = onPrivacyPolicy,
        onTermsOfService = onTermsOfService,
        showRecoveryPopup = showRecoveryPopup,
        onDismissRecoveryPopup = { showRecoveryPopup = false },
        isSigningIn = isSigningIn,
        modifier = modifier
    )
}

@Composable
private fun CloudAccountSignInContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    serverDomain: String,
    onServerDomainChange: (String) -> Unit,
    isServerEditable: Boolean,
    onServerDomainDoubleClick: () -> Unit,
    onServerFocusLost: () -> Unit,
    serverFocusRequester: FocusRequester,
    onSignIn: () -> Unit,
    onAccountRecovery: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsOfService: () -> Unit,
    showRecoveryPopup: Boolean,
    onDismissRecoveryPopup: () -> Unit,
    isSigningIn: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                Spacer(modifier = Modifier.height(Spacing.xxl))
                
                // Logo
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    border = null
                ) {
                    Box(
                        modifier = Modifier.padding(Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "LogDate Cloud",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                // Title
                Text(
                    text = "Sign In to LogDate Cloud",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // Sign-in form
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    UsernameWithServerField(
                        username = username,
                        onUsernameChange = onUsernameChange,
                        serverDomain = serverDomain,
                        onServerDomainChange = onServerDomainChange,
                        isServerEditable = isServerEditable,
                        onServerDomainDoubleClick = onServerDomainDoubleClick,
                        onServerFocusLost = onServerFocusLost,
                        serverFocusRequester = serverFocusRequester
                    )
                    
                    Button(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSigningIn
                    ) {
                        if (isSigningIn) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Text("Signing In...")
                            }
                        } else {
                            Text("Sign In with Passkey")
                        }
                    }
                    
                    TextButton(
                        onClick = onAccountRecovery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Account Recovery")
                    }
                }
            }
            
            // Footer with privacy policy and terms
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onPrivacyPolicy() }
                    )
                    
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onTermsOfService() }
                    )
                }
            }
        }
        
        // Recovery popup
        if (showRecoveryPopup) {
            RecoveryPopup(
                onDismiss = onDismissRecoveryPopup,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun UsernameWithServerField(
    username: String,
    onUsernameChange: (String) -> Unit,
    serverDomain: String,
    onServerDomainChange: (String) -> Unit,
    isServerEditable: Boolean,
    onServerDomainDoubleClick: () -> Unit,
    onServerFocusLost: () -> Unit,
    serverFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val serverInteractionSource = remember { MutableInteractionSource() }
    val isHovered by serverInteractionSource.collectIsHoveredAsState()
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "Username",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Box {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("your-username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    )
                )
                
                Text(
                    text = "@",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isServerEditable) {
                    OutlinedTextField(
                        value = serverDomain,
                        onValueChange = onServerDomainChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(serverFocusRequester),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .hoverable(serverInteractionSource)
                            .clickable(
                                interactionSource = serverInteractionSource,
                                indication = null
                            ) { onServerDomainDoubleClick() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = serverDomain,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Tooltip that appears on hover
            if (isHovered && !isServerEditable) {
                Popup(
                    alignment = Alignment.TopCenter,
                    offset = androidx.compose.ui.unit.IntOffset(0, -8),
                    properties = PopupProperties(focusable = false)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.inverseSurface
                        ),
                        border = null
                    ) {
                        Text(
                            text = "Double-click to use a custom server",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(Spacing.lg)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = null
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Account Recovery",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Coming soon! Account recovery features are being developed.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Got it")
            }
        }
    }
}

@Preview
@Composable
private fun CloudAccountSignInScreenPreview() {
    MaterialTheme {
        Surface {
            CloudAccountSignInContent(
                username = "john-doe",
                onUsernameChange = {},
                serverDomain = "logdate.app",
                onServerDomainChange = {},
                isServerEditable = false,
                onServerDomainDoubleClick = {},
                onServerFocusLost = {},
                serverFocusRequester = FocusRequester(),
                onSignIn = {},
                onAccountRecovery = {},
                onPrivacyPolicy = {},
                onTermsOfService = {},
                showRecoveryPopup = false,
                onDismissRecoveryPopup = {},
                isSigningIn = false
            )
        }
    }
}