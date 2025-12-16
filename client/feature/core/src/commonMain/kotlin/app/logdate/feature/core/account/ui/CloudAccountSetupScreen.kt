package app.logdate.feature.core.account.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Main container screen that manages the cloud account setup flow.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountSetupScreen(
    viewModel: CloudAccountSetupViewModel,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show error messages in snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    // Handle completion
    LaunchedEffect(uiState.isAccountCreated, uiState.isSkipped) {
        if (uiState.isAccountCreated) {
            onComplete()
        } else if (uiState.isSkipped) {
            onSkip()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(getScreenTitle(uiState.currentStep)) },
                navigationIcon = {
                    if (uiState.currentStep != SetupStep.INTRO) {
                        IconButton(onClick = { viewModel.goToPreviousStep() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main content based on the current step
            when (uiState.currentStep) {
                SetupStep.INTRO -> IntroContent(
                    onContinue = { viewModel.goToNextStep() },
                    onSkip = { viewModel.skipCloudSetup() }
                )
                SetupStep.USERNAME_SELECTION -> UsernameSelectionContent(
                    username = uiState.username,
                    onUsernameChange = { viewModel.updateUsername(it) },
                    usernameError = uiState.usernameError,
                    usernameAvailability = uiState.usernameAvailability,
                    isCheckingAvailability = uiState.isCheckingAvailability,
                    canProceed = uiState.canProceedFromUsername,
                    onCheckAvailability = { viewModel.checkUsernameAvailability() },
                    onContinue = { viewModel.goToNextStep() }
                )
                SetupStep.DISPLAY_NAME_SELECTION -> DisplayNameSelectionContent(
                    displayName = uiState.displayName,
                    onDisplayNameChange = { viewModel.updateDisplayName(it) },
                    displayNameError = uiState.displayNameError,
                    bio = uiState.bio,
                    onBioChange = { viewModel.updateBio(it) },
                    canProceed = uiState.canProceedFromDisplayName,
                    onContinue = { viewModel.goToNextStep() }
                )
                SetupStep.PASSKEY_CREATION -> PasskeyCreationContent(
                    isCreatingPasskey = uiState.isCreatingPasskey,
                    passkeyCreated = uiState.passkeyCreated,
                    canCreatePasskey = uiState.canCreatePasskey,
                    onCreatePasskey = { viewModel.createPasskey() }
                )
                SetupStep.COMPLETION -> CompletionContent(
                    username = uiState.username,
                    onComplete = onComplete
                )
            }
        }
    }
}

@Composable
private fun IntroContent(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Create a Cloud Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Create an account to sync your journals across devices and unlock additional features.",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun UsernameSelectionContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameError: String?,
    usernameAvailability: UsernameAvailability,
    isCheckingAvailability: Boolean,
    canProceed: Boolean,
    onCheckAvailability: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Choose a username",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This will be your unique identifier on LogDate Cloud.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            isError = usernameError != null,
            supportingText = {
                when {
                    usernameError != null -> Text(usernameError)
                    usernameAvailability == UsernameAvailability.AVAILABLE -> 
                        Text("Username is available", color = MaterialTheme.colorScheme.primary)
                    usernameAvailability == UsernameAvailability.TAKEN -> 
                        Text("Username is already taken", color = MaterialTheme.colorScheme.error)
                    usernameAvailability == UsernameAvailability.ERROR ->
                        Text("Error checking availability", color = MaterialTheme.colorScheme.error)
                }
            },
            trailingIcon = {
                if (isCheckingAvailability) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onCheckAvailability,
            enabled = username.isNotBlank() && !isCheckingAvailability,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check Availability")
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onContinue,
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun DisplayNameSelectionContent(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    displayNameError: String?,
    bio: String,
    onBioChange: (String) -> Unit,
    canProceed: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Complete Your Profile",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display Name") },
            isError = displayNameError != null,
            supportingText = displayNameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            label = { Text("Bio (Optional)") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onContinue,
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun PasskeyCreationContent(
    isCreatingPasskey: Boolean,
    passkeyCreated: Boolean,
    canCreatePasskey: Boolean,
    onCreatePasskey: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Secure Your Account",
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Create a passkey to securely sign in to your account across all your devices.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isCreatingPasskey) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Creating your passkey...")
        } else if (passkeyCreated) {
            Text(
                text = "Passkey created successfully!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onCreatePasskey,
            enabled = canCreatePasskey && !isCreatingPasskey && !passkeyCreated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Passkey")
        }
    }
}

@Composable
private fun CompletionContent(
    username: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "Welcome to LogDate Cloud",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Your account has been created successfully. You are now signed in as $username.",
            style = MaterialTheme.typography.bodyLarge
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}

private fun getScreenTitle(step: SetupStep): String {
    return when (step) {
        SetupStep.INTRO -> "Cloud Account"
        SetupStep.USERNAME_SELECTION -> "Choose Username"
        SetupStep.DISPLAY_NAME_SELECTION -> "Profile Details"
        SetupStep.PASSKEY_CREATION -> "Secure Account"
        SetupStep.COMPLETION -> "Setup Complete"
    }
}