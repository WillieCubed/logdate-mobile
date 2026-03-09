@file:Suppress("ktlint:standard:function-naming")

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
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.back
import logdate.client.feature.core.generated.resources.check_availability
import logdate.client.feature.core.generated.resources.choose_a_username
import logdate.client.feature.core.generated.resources.`continue`
import logdate.client.feature.core.generated.resources.this_username_will_identify_you_on_logdate_cloud_you_can_change_it_later
import logdate.client.feature.core.generated.resources.username
import logdate.client.feature.core.generated.resources.username_is_already_taken
import logdate.client.feature.core.generated.resources.username_is_available_3
import org.jetbrains.compose.resources.stringResource
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
    viewModel: AccountOnboardingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
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

    UsernameSelectionContent(
        uiState = uiState,
        onUsernameChange = viewModel::onUsernameChanged,
        onCheckAvailability = viewModel::checkUsernameAvailability,
        onContinue = {
            viewModel.onUsernameContinue()
            onContinue()
        },
        onBack = onBack,
        focusRequester = focusRequester,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
fun UsernameSelectionContent(
    uiState: AccountOnboardingUiState,
    onUsernameChange: (String) -> Unit,
    onCheckAvailability: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.choose_a_username),
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = stringResource(Res.string.this_username_will_identify_you_on_logdate_cloud_you_can_change_it_later),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(Res.string.username)) },
                isError = uiState.usernameError != null,
                supportingText = uiState.usernameError?.let { { Text(it) } },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (uiState.canContinueFromUsername) {
                                onContinue()
                            } else {
                                onCheckAvailability()
                            }
                        },
                    ),
                trailingIcon = {
                    if (uiState.usernameAvailability == UsernameAvailability.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                singleLine = true,
            )

            Button(
                onClick = onCheckAvailability,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canCheckUsernameAvailability,
            ) {
                Text(stringResource(Res.string.check_availability))
            }

            if (uiState.usernameAvailability == UsernameAvailability.AVAILABLE) {
                Text(
                    text = stringResource(Res.string.username_is_available_3),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (uiState.usernameAvailability == UsernameAvailability.TAKEN) {
                Text(
                    text = stringResource(Res.string.username_is_already_taken),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canContinueFromUsername,
            ) {
                Text(stringResource(Res.string.`continue`))
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.back))
            }
        }
    }
}
