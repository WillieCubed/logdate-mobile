# Passkeys Feature Module

This module provides UI components and view models for passkey authentication in LogDate.

## Features

- **Passkey Registration**: Register new passkeys for password-less authentication
- **Passkey Authentication**: Authenticate using existing passkeys
- **Passkey Management**: View and manage registered passkeys
- **Platform Detection**: Automatically detects passkey support on the current platform

## Usage

### Registration

```kotlin
@Composable
fun PasskeyRegistrationScreen(
    viewModel: PasskeyRegistrationViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    when (uiState) {
        is PasskeyRegistrationUiState.Idle -> {
            PasskeyRegistrationForm(
                onRegister = { username, displayName ->
                    viewModel.startRegistration(username, displayName)
                }
            )
        }
        is PasskeyRegistrationUiState.Loading -> {
            CircularProgressIndicator()
        }
        is PasskeyRegistrationUiState.Success -> {
            Text("Passkey registered successfully!")
        }
        is PasskeyRegistrationUiState.Error -> {
            Text("Error: ${uiState.message}")
        }
    }
}
```

### Authentication

```kotlin
@Composable
fun PasskeyAuthenticationScreen(
    viewModel: PasskeyAuthenticationViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Button(
        onClick = { viewModel.startAuthentication() }
    ) {
        Text("Sign in with Passkey")
    }
    
    when (uiState) {
        is PasskeyAuthenticationUiState.Loading -> {
            CircularProgressIndicator()
        }
        is PasskeyAuthenticationUiState.Success -> {
            // Navigate to main app
        }
        is PasskeyAuthenticationUiState.Error -> {
            Text("Authentication failed: ${uiState.message}")
        }
    }
}
```

## Integration with LogDate Auth

The passkey feature integrates with the existing authentication system by:

1. **Registration**: After successful passkey registration, the user is automatically signed in
2. **Authentication**: Successful passkey authentication creates a session token
3. **Fallback**: Traditional email/password authentication remains available
4. **Migration**: Existing users can add passkeys to their accounts

## Platform Support

- **Android**: Full support via Android Credential Manager API (Android 9+)
- **iOS**: Full support via AuthenticationServices (iOS 16+)
- **Desktop**: Limited support (requires browser-based WebAuthn)

## Security Considerations

- All passkey operations use the device's secure enclave/TPM
- Private keys never leave the device
- Server-side verification uses WebAuthn4J library
- Challenges are time-limited and single-use
- User verification (biometrics/PIN) is required for sensitive operations