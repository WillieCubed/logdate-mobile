package app.logdate.feature.account.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.credentials.CredentialManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.logdate.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@Composable
fun AccountCreationScreen(
    viewModel: AccountFlowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {

    }
}

enum class AccountCreationStep {
    USERNAME,
    MFA,
    SUCCESS,
}

data class AccountCreationUiState(
    val domain: String = "",
    val step: AccountCreationStep = AccountCreationStep.USERNAME,
)

@HiltViewModel
class AccountFlowViewModel @Inject constructor(
    @ApplicationContext context: Context
) : ViewModel() {
    private val TAG = AccountFlowViewModel::class.java.simpleName

    private val _uiState = MutableStateFlow(AccountCreationUiState())

    private val credentialManager = CredentialManager.create(context)

    val uiState: MutableStateFlow<AccountCreationUiState> = _uiState

//    fun createPasskey(requestJson: String, preferImmediatelyAvailableCredentials: Boolean) {
////        val createPublicKeyCredentialRequest = CreatePublicKeyCredentialRequest(
////            // Contains the request in JSON format. Uses the standard WebAuthn
////            // web JSON spec.
////            requestJson = requestJson,
////            // Defines whether you prefer to use only immediately available
////            // credentials, not hybrid credentials, to fulfill this request.
////            // This value is false by default.
////            preferImmediatelyAvailableCredentials = preferImmediatelyAvailableCredentials,
////        )
//
//        // Execute CreateCredentialRequest asynchronously to register credentials
//        // for a user account. Handle success and failure cases with the result and
//        // exceptions, respectively.
//        viewModelScope.launch {
//            try {
////                val result = credentialManager.createCredential(
////                    // Use an activity-based context to avoid undefined system
////                    // UI launching behavior
////                    context = context,
////                    request = createPublicKeyCredentialRequest,
////                )
////                handlePasskeyRegistrationResult(result)
//            } catch (e: CreateCredentialException) {
//                handleFailure(e)
//            }
//        }
//    }
//
//    private fun handlePasskeyRegistrationResult(result: CreateCredentialResponse) {
//        TODO("Not yet implemented")
//    }
//
//    fun handleFailure(e: CreateCredentialException) {
//        when (e) {
//            is CreatePublicKeyCredentialDomException -> {
//                // Handle the passkey DOM errors thrown according to the
//                // WebAuthn spec.
//                handlePasskeyError(e.domError)
//            }
//
//            is CreateCredentialCancellationException -> {
//                // The user intentionally canceled the operation and chose not
//                // to register the credential.
//            }
//
//            is CreateCredentialInterruptedException -> {
//                // Retry-able error. Consider retrying the call.
//            }
//
//            is CreateCredentialProviderConfigurationException -> {
//                // Your app is missing the provider configuration dependency.
//                // Most likely, you're missing the
//                // "credentials-play-services-auth" module.
//            }
//
//            is CreateCredentialUnknownException -> {}
//            is CreateCredentialCustomException -> {
//                // You have encountered an error from a 3rd-party SDK. If you
//                // make the API call with a request object that's a subclass of
//                // CreateCustomCredentialRequest using a 3rd-party SDK, then you
//                // should check for any custom exception type constants within
//                // that SDK to match with e.type. Otherwise, drop or log the
//                // exception.
//            }
//
//            else -> Log.w(TAG, "Unexpected exception type ${e::class.java.name}")
//        }
//    }
//
//    private fun handlePasskeyError(domError: DomError) {
//        // Handle the passkey DOM errors thrown according to the WebAuthn spec.
//        Log.e(TAG, "Passkey DOM error: $domError")
//    }
//
//    fun signIn() {
//        // Retrieves the user's saved password for your app from their
//        // password provider.
//        val getPasswordOption = GetPasswordOption()
//
//        // Get passkey from the user's public key credential provider.
////        val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
////            requestJson = requestJson
////        )
//
//        viewModelScope.launch {
////            try {
////                val result = credentialManager.getCredential(
////                    // Use an activity-based context to avoid undefined system UI
////                    // launching behavior.
////                    context = activityContext,
////                    request = getCredRequest
////                )
////                handleSignIn(result)
////            } catch (e: GetCredentialException) {
////                handleFailure(e)
////            }
//        }
//    }
//
//    private fun handleSignIn(result: GetCredentialResponse) {
//        when (val credential = result.credential) {
//            is PublicKeyCredential -> {
//                val responseJson = credential.authenticationResponseJson
//                // Share responseJson i.e. a GetCredentialResponse on your server to
//                // validate and  authenticate
//            }
//
//            is PasswordCredential -> {
//                val username = credential.id
//                val password = credential.password
//                // Use id and password to send to your server to validate
//                // and authenticate
//            }
//
//            is CustomCredential -> {
////                // If you are also using any external sign-in libraries, parse them
////                // here with the utility functions provided.
////                if (credential.type == ExampleCustomCredential.TYPE)  {
////                    try {
////                        val ExampleCustomCredential = ExampleCustomCredential.createFrom(credential.data)
////                        // Extract the required credentials and complete the authentication as per
////                        // the federated sign in or any external sign in library flow
////                    } catch (e: ExampleCustomCredential.ExampleCustomCredentialParsingException) {
////                        // Unlikely to happen. If it does, you likely need to update the dependency
////                        // version of your external sign-in library.
////                        Log.e(TAG, "Failed to parse an ExampleCustomCredential", e)
////                    }
////                } else {
////                    // Catch any unrecognized custom credential type here.
////                    Log.e(TAG, "Unexpected type of credential")
////                }
////            } else -> {
////            // Catch any unrecognized credential type here.
////            Log.e(TAG, "Unexpected type of credential")
//            }
//        }
//    }
}