package app.logdate.feature.core.account.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.logdate.ui.GenericLoadingScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for creating a passkey during account setup.
 * 
 * This screen guides the user through the process of creating a passkey 
 * for secure authentication with their LogDate Cloud account.
 * 
 * @param onComplete Callback when the passkey is created and account setup is complete.
 * @param onBack Callback when the user chooses to go back.
 * @param viewModel The ViewModel for this screen.
 */
@Composable
fun PasskeyCreationScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: PasskeyCreationViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Show error messages in a Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            viewModel.clearErrorMessage()
        }
    }
    
    // Handle account creation completion
    LaunchedEffect(uiState.accountCreated) {
        if (uiState.accountCreated) {
            onComplete()
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isCreatingAccount -> {
                    // Show loading screen with text
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Creating your LogDate Cloud account...")
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Create a Passkey",
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "Passkeys are a secure way to sign in without passwords. " +
                                   "Your device will verify it's you using biometrics or a PIN.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        PasskeyInfoCard()
                        
                        AnimatedVisibility(
                            visible = uiState.isCreatingPasskey,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Creating your passkey...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Button(
                            onClick = { viewModel.createPasskey() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isCreatingPasskey && !uiState.isCreatingAccount
                        ) {
                            Text("Create Passkey")
                        }
                        
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isCreatingPasskey && !uiState.isCreatingAccount
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Informational card explaining passkeys to users.
 */
@Composable
private fun PasskeyInfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PasskeyInfoItem(
            title = "Simple and Secure",
            description = "No more forgotten passwords. Use your device's biometrics or PIN instead."
        )
        
        PasskeyInfoItem(
            title = "Works Across Devices",
            description = "Your passkey can be used on all your devices through your platform account."
        )
        
        PasskeyInfoItem(
            title = "Phishing Resistant",
            description = "Passkeys are linked to the app and cannot be used on fake websites."
        )
    }
}

/**
 * Individual passkey information item.
 */
@Composable
private fun PasskeyInfoItem(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}