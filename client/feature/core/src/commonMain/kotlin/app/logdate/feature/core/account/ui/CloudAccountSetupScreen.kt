@file:Suppress("ktlint:standard:function-naming")

package app.logdate.feature.core.account.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.account_created_signed_in_as
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.bio_optional
import logdate.client.feature.core.generated.resources.check_availability
import logdate.client.feature.core.generated.resources.choose_a_username_2
import logdate.client.feature.core.generated.resources.complete_your_profile
import logdate.client.feature.core.generated.resources.`continue`
import logdate.client.feature.core.generated.resources.create_a_cloud_account
import logdate.client.feature.core.generated.resources.create_a_passkey_to_securely_sign_in_to_your_account_across_all_your_devices
import logdate.client.feature.core.generated.resources.create_an_account_to_sync_your_journals_across_devices_and_unlock_additional_features
import logdate.client.feature.core.generated.resources.create_passkey
import logdate.client.feature.core.generated.resources.creating_your_passkey
import logdate.client.feature.core.generated.resources.display_name
import logdate.client.feature.core.generated.resources.error_checking_availability
import logdate.client.feature.core.generated.resources.get_started
import logdate.client.feature.core.generated.resources.passkey_created_successfully
import logdate.client.feature.core.generated.resources.secure_your_account
import logdate.client.feature.core.generated.resources.skip_for_now
import logdate.client.feature.core.generated.resources.this_will_be_your_unique_identifier_on_logdate_cloud
import logdate.client.feature.core.generated.resources.username
import logdate.client.feature.core.generated.resources.username_is_already_taken
import logdate.client.feature.core.generated.resources.username_is_available
import logdate.client.feature.core.generated.resources.welcome_to_logdate_cloud
import org.jetbrains.compose.resources.stringResource

/**
 * Main container screen that manages the cloud account setup flow.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CloudAccountSetupScreen(
    viewModel: CloudAccountSetupViewModel,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
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
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Main content based on the current step
            when (uiState.currentStep) {
                SetupStep.INTRO ->
                    IntroContent(
                        onContinue = { viewModel.goToNextStep() },
                        onSkip = { viewModel.skipCloudSetup() },
                    )
                SetupStep.USERNAME_SELECTION ->
                    UsernameSelectionContent(
                        username = uiState.username,
                        onUsernameChange = { viewModel.updateUsername(it) },
                        usernameError = uiState.usernameError,
                        usernameAvailability = uiState.usernameAvailability,
                        isCheckingAvailability = uiState.isCheckingAvailability,
                        canProceed = uiState.canProceedFromUsername,
                        onCheckAvailability = { viewModel.checkUsernameAvailability() },
                        onContinue = { viewModel.goToNextStep() },
                    )
                SetupStep.DISPLAY_NAME_SELECTION ->
                    DisplayNameSelectionContent(
                        displayName = uiState.displayName,
                        onDisplayNameChange = { viewModel.updateDisplayName(it) },
                        displayNameError = uiState.displayNameError,
                        bio = uiState.bio,
                        onBioChange = { viewModel.updateBio(it) },
                        canProceed = uiState.canProceedFromDisplayName,
                        onContinue = { viewModel.goToNextStep() },
                    )
                SetupStep.PASSKEY_CREATION ->
                    PasskeyCreationContent(
                        isCreatingPasskey = uiState.isCreatingPasskey,
                        passkeyCreated = uiState.passkeyCreated,
                        canCreatePasskey = uiState.canCreatePasskey,
                        onCreatePasskey = { viewModel.createPasskey() },
                    )
                SetupStep.COMPLETION ->
                    CompletionContent(
                        username = uiState.username,
                        onComplete = onComplete,
                    )
            }
        }
    }
}

@Composable
private fun IntroContent(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(Res.string.create_a_cloud_account),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.create_an_account_to_sync_your_journals_across_devices_and_unlock_additional_features),
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.`continue`))
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.skip_for_now))
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.choose_a_username_2),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.this_will_be_your_unique_identifier_on_logdate_cloud),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(Res.string.username)) },
            isError = usernameError != null,
            supportingText = {
                when {
                    usernameError != null -> Text(usernameError)
                    usernameAvailability == UsernameAvailability.AVAILABLE ->
                        Text(stringResource(Res.string.username_is_available), color = MaterialTheme.colorScheme.primary)
                    usernameAvailability == UsernameAvailability.TAKEN ->
                        Text(stringResource(Res.string.username_is_already_taken), color = MaterialTheme.colorScheme.error)
                    usernameAvailability == UsernameAvailability.ERROR ->
                        Text(stringResource(Res.string.error_checking_availability), color = MaterialTheme.colorScheme.error)
                }
            },
            trailingIcon = {
                if (isCheckingAvailability) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCheckAvailability,
            enabled = username.isNotBlank() && !isCheckingAvailability,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.check_availability))
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.`continue`))
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.complete_your_profile),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(Res.string.display_name)) },
            isError = displayNameError != null,
            supportingText = displayNameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            label = { Text(stringResource(Res.string.bio_optional)) },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.`continue`))
        }
    }
}

@Composable
private fun PasskeyCreationContent(
    isCreatingPasskey: Boolean,
    passkeyCreated: Boolean,
    canCreatePasskey: Boolean,
    onCreatePasskey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.secure_your_account),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.create_a_passkey_to_securely_sign_in_to_your_account_across_all_your_devices),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isCreatingPasskey) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(Res.string.creating_your_passkey))
        } else if (passkeyCreated) {
            Text(
                text = stringResource(Res.string.passkey_created_successfully),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onCreatePasskey,
            enabled = canCreatePasskey && !isCreatingPasskey && !passkeyCreated,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.create_passkey))
        }
    }
}

@Composable
private fun CompletionContent(
    username: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(Res.string.welcome_to_logdate_cloud),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text =
                stringResource(
                    Res.string.account_created_signed_in_as,
                    username,
                ),
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.get_started))
        }
    }
}

private fun getScreenTitle(step: SetupStep): String =
    when (step) {
        SetupStep.INTRO -> "Cloud Account"
        SetupStep.USERNAME_SELECTION -> "Choose Username"
        SetupStep.DISPLAY_NAME_SELECTION -> "Profile Details"
        SetupStep.PASSKEY_CREATION -> "Secure Account"
        SetupStep.COMPLETION -> "Setup Complete"
    }
