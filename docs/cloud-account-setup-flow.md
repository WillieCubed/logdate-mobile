# LogDate Cloud Account Setup Flow

This document outlines the architecture, design decisions, and implementation details of the LogDate Cloud account setup flow. This flow enables users to create a cloud account using passkeys for secure, passwordless authentication.

## Overview

The LogDate Cloud account setup flow is a multi-step process that guides users through creating a cloud account. It is designed to be accessible from multiple entry points in the app, including onboarding and settings, and uses passkeys for secure authentication.

### Key Features

- **Email-Optional Registration**: No email required to create an account
- **Secure Passkey Authentication**: Uses WebAuthn/FIDO2 passkeys for authentication
- **Cross-Platform Support**: Works on Android, iOS, and Desktop
- **Consistent User Experience**: Follows LogDate UX design principles
- **Adaptive Flow**: Behavior adapts based on entry point (onboarding vs. settings)

## Architecture

The implementation follows clean architecture principles with:

1. **UI Layer**: Screens and ViewModels for user interaction
2. **Domain Layer**: Business logic and model definitions
3. **Data Layer**: API clients and repositories for data access

### Core Components

#### Domain Models

- `CloudAccount`: Represents user account information
- `PasskeyCredential`: Represents passkey authentication credentials
- `UserIdentity`: Manages user identity information

#### Repositories

- `CloudAccountRepository`: Interface for cloud account operations
- `DefaultCloudAccountRepository`: Implementation of the repository

#### API Clients

- `CloudApiClient`: Interface for cloud API communication
- `LogDateCloudApiClient`: Implementation of the client

#### Platform-Specific Components

- `PasskeyManager`: Interface for passkey operations
- Platform implementations:
  - `AndroidPasskeyManager`: Uses Credential Manager API
  - `IosPasskeyManager`: Uses AuthenticationServices
  - `DesktopPasskeyManager`: Provides a stub implementation

#### UI Screens and ViewModels

- `CloudAccountIntroScreen` and `CloudAccountIntroViewModel`
- `UsernameSelectionScreen` and `UsernameSelectionViewModel`
- `DisplayNameSelectionScreen` and `DisplayNameSelectionViewModel`
- `PasskeyCreationScreen` and `PasskeyCreationViewModel`
- `AccountCreationCompletionScreen` and `AccountCreationCompletionViewModel`

#### Navigation

- `CloudAccountRoutes`: Route definitions
- `CloudAccountNavigation`: Navigation setup
- `CloudAccountSetupCoordinator`: Manages flow transitions

## Flow Sequence

1. **Introduction**: User sees benefits of LogDate Cloud
2. **Username Selection**: User chooses a unique username
3. **Display Name Selection**: User enters their display name
4. **Passkey Creation**: System creates a passkey for authentication
5. **Account Creation**: Backend creates the account
6. **Completion**: User is informed of successful creation

## Authentication Details

The authentication uses WebAuthn/FIDO2 passkeys:

1. **Registration Process**:
   - Server generates a challenge
   - Client creates a public/private key pair
   - Private key is stored securely on the device
   - Public key is sent to the server
   
2. **Authentication Process**:
   - Server sends a challenge
   - Client signs the challenge with the private key
   - Server verifies the signature with the public key

## Platform-Specific Implementations

### Android

- Uses `androidx.credentials.CredentialManager` for passkey operations
- Integrates with Android's account system via `AccountManager`
- Supports biometric verification (fingerprint, face recognition)

### iOS

- Uses `AuthenticationServices` framework for passkey operations
- Integrates with iOS Keychain for credential storage
- Supports Face ID/Touch ID for verification

### Desktop

- Currently uses a stub implementation with simulated passkey operations
- Future enhancement will integrate with system-specific credential providers

## Data Flow

1. **Username Availability**: App → API → App
2. **Begin Account Creation**: App → API → App
3. **Passkey Creation**: App → Platform APIs → App
4. **Complete Account Creation**: App → API → App

## Error Handling

The implementation includes comprehensive error handling:

1. **Validation Errors**: Form validation with immediate feedback
2. **API Errors**: Structured error responses with error codes
3. **Passkey Errors**: Platform-specific errors mapped to domain model
4. **Retry Mechanisms**: For transient failures
5. **User Feedback**: Snackbars for error messages

## Security Considerations

1. **No Password Storage**: Passkeys eliminate need for password storage
2. **Secure Communication**: All API calls use HTTPS
3. **Local Data Protection**: Credentials stored in secure platform storage
4. **Key Protection**: Private keys never leave the device
5. **Verification**: User verification required for passkey operations

## Testing Strategy

1. **Unit Tests**: For business logic and validation
2. **Integration Tests**: For API clients and repositories
3. **UI Tests**: For screen functionality and user flows

## Dependencies

The implementation leverages existing infrastructure:

1. **Koin**: For dependency injection
2. **Ktor**: For API communication
3. **Platform APIs**: For passkey operations
4. **Compose**: For UI implementation
5. **Navigation3**: For screen navigation

## Future Improvements

1. **Multiple Passkeys**: Support for multiple passkeys per account
2. **Passkey Recovery**: Improved recovery options
3. **Desktop Integration**: Full passkey support on desktop platforms
4. **Identity Federation**: Support for federated identity providers

## Conclusion

The LogDate Cloud account setup flow provides a secure, user-friendly way for users to create and access cloud accounts across all platforms. By using passkeys, we eliminate the security risks associated with passwords while maintaining a seamless user experience.