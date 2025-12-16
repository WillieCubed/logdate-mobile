package app.logdate.feature.core.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for selecting a username during account creation.
 * 
 * This screen allows users to choose a unique username for their LogDate Cloud account.
 * It performs real-time validation and availability checks as the user types.
 * 
 * @param onContinue Callback when a valid username is selected and the user continues.
 * @param onBack Callback when the user chooses to go back.
 * @param viewModel The ViewModel for this screen.
 */
@Composable
fun UsernameSelectionScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    viewModel: AccountOnboardingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Request focus on the username field when the screen appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Show error messages in a Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(message = it)
            viewModel.clearErrorMessage()
        }
    }
    
    // No longer needed - handle navigation directly in button clicks
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Choose a Username",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = "This username will identify you on LogDate Cloud. You can change it later.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.onUsernameChanged(it) },
                label = { Text("Username") },
                isError = uiState.usernameError != null,
                supportingText = uiState.usernameError?.let { { Text(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (uiState.canContinueFromUsername) {
                            viewModel.onUsernameContinue()
                            onContinue()
                        } else {
                            viewModel.checkUsernameAvailability()
                        }
                    }
                ),
                trailingIcon = {
                    if (uiState.usernameAvailability == UsernameAvailability.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                },
                singleLine = true
            )
            
            Button(
                onClick = { viewModel.checkUsernameAvailability() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canCheckUsernameAvailability
            ) {
                Text("Check Availability")
            }
            
            if (uiState.usernameAvailability == UsernameAvailability.AVAILABLE) {
                Text(
                    text = "Username is available!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (uiState.usernameAvailability == UsernameAvailability.TAKEN) {
                Text(
                    text = "Username is already taken",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    viewModel.onUsernameContinue()
                    onContinue()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canContinueFromUsername
            ) {
                Text("Continue")
            }
            
            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Back")
            }
        }
    }
}