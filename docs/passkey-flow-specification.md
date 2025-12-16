# LogDate Cloud Passkey Authentication Flow Specification

## Overview

LogDate Cloud implements a passwordless authentication system using WebAuthn passkeys, allowing users to create and access accounts without requiring email addresses or passwords. This document outlines the complete flow for account creation, authentication, and account management using passkeys.

## Core Principles

1. **Email-Optional**: Users can create accounts using only passkeys, no email required
2. **Device-Bound Security**: Private keys never leave the user's device
3. **Cross-Platform**: Seamless experience across Android, iOS, and web platforms
4. **Standards Compliant**: Full WebAuthn/FIDO2 specification compliance
5. **Account Persistence**: Integration with platform account managers for seamless re-authentication

## Account Creation Flow

### 1. Initiate Account Creation

```
User Action: "Create Account with Passkey"
↓
Client → Server: POST /api/v1/accounts/create/begin
{
  "username": "johndoe123",
  "displayName": "John Doe",
  "bio": "Optional user bio"
}
```

### 2. Server Generates Registration Challenge

```
Server Response: 200 OK
{
  "challenge": "base64url_encoded_challenge",
  "userId": "base64url_encoded_temporary_user_id",
  "sessionId": "temporary_session_for_this_flow",
  "registrationOptions": {
    "rp": {
      "id": "logdate.app",
      "name": "LogDate Cloud"
    },
    "user": {
      "id": "base64url_encoded_temporary_user_id",
      "name": "generated_username_or_preferred",
      "displayName": "Generated Display Name"
    },
    "challenge": "base64url_encoded_challenge",
    "pubKeyCredParams": [
      {"type": "public-key", "alg": -7},   // ES256
      {"type": "public-key", "alg": -257}  // RS256
    ],
    "timeout": 300000,
    "authenticatorSelection": {
      "authenticatorAttachment": "platform",
      "requireResidentKey": true,
      "residentKey": "required",
      "userVerification": "required"
    },
    "attestation": "none"
  }
}
```

### 3. Client Performs Passkey Registration

```
Client Action:
1. Extract registrationOptions from server response
2. Call platform WebAuthn API (Android Credential Manager, iOS AuthenticationServices)
3. User completes biometric/PIN verification
4. Receive credential response from platform
```

### 4. Complete Account Creation

```
Client → Server: POST /api/v1/auth/passkey/account/complete
{
  "sessionId": "temporary_session_from_step_2",
  "credential": {
    "id": "credential_id",
    "rawId": "base64url_encoded_raw_id",
    "response": {
      "clientDataJSON": "base64url_encoded_client_data",
      "attestationObject": "base64url_encoded_attestation"
    },
    "type": "public-key"
  },
  "accountPreferences": {
    "displayName": "user_chosen_display_name",
    "timezone": "America/New_York",
    "locale": "en-US"
  }
}
```

### 5. Server Creates Account and Returns Session

```
Server Response: 201 Created
{
  "success": true,
  "account": {
    "userId": "permanent_uuid",
    "username": "generated_or_chosen_username",
    "displayName": "User Display Name",
    "createdAt": "2024-01-15T10:30:00Z",
    "accountId": "logdate_cloud_account_id"
  },
  "session": {
    "accessToken": "jwt_access_token",
    "refreshToken": "jwt_refresh_token",
    "expiresIn": 3600
  },
  "passkey": {
    "credentialId": "credential_id",
    "nickname": "Generated from device info",
    "createdAt": "2024-01-15T10:30:00Z"
  },
  "syncData": {
    "serverEndpoint": "https://api.logdate.app",
    "initialSyncRequired": true
  }
}
```

### 6. Client Stores Account Information

```
Client Actions:
1. Store session tokens securely (Keychain/KeyStore)
2. Register account with system AccountManager
3. Initialize local app state with account data
4. Begin initial data sync
```

## Authentication Flow (Existing Account)

### 1. Initiate Authentication

```
User Action: "Sign In with Passkey"
↓
Client → Server: POST /api/v1/auth/passkey/signin/begin
{
  "accountHint": "optional_username_or_account_id",
  "deviceInfo": {
    "platform": "android|ios|web",
    "deviceName": "User's iPhone"
  }
}
```

### 2. Server Generates Authentication Challenge

```
Server Response: 200 OK
{
  "challenge": "base64url_encoded_challenge",
  "sessionId": "temporary_session_for_this_flow",
  "authenticationOptions": {
    "challenge": "base64url_encoded_challenge",
    "timeout": 300000,
    "rpId": "logdate.app",
    "allowCredentials": [
      // Optional: if account hint provided, include known credentials
      {
        "type": "public-key",
        "id": "base64url_encoded_credential_id",
        "transports": ["internal", "hybrid"]
      }
    ],
    "userVerification": "required"
  }
}
```

### 3. Client Performs Passkey Authentication

```
Client Action:
1. Extract authenticationOptions from server response
2. Call platform WebAuthn API
3. User completes biometric/PIN verification
4. Receive assertion response from platform
```

### 4. Complete Authentication

```
Client → Server: POST /api/v1/auth/passkey/signin/complete
{
  "sessionId": "temporary_session_from_step_2",
  "credential": {
    "id": "credential_id",
    "rawId": "base64url_encoded_raw_id",
    "response": {
      "clientDataJSON": "base64url_encoded_client_data",
      "authenticatorData": "base64url_encoded_auth_data",
      "signature": "base64url_encoded_signature",
      "userHandle": "base64url_encoded_user_id"
    },
    "type": "public-key"
  }
}
```

### 5. Server Validates and Returns Session

```
Server Response: 200 OK
{
  "success": true,
  "account": {
    "userId": "permanent_uuid",
    "username": "user_username",
    "displayName": "User Display Name",
    "lastSignIn": "2024-01-15T10:30:00Z"
  },
  "session": {
    "accessToken": "jwt_access_token",
    "refreshToken": "jwt_refresh_token", 
    "expiresIn": 3600
  },
  "syncData": {
    "lastSyncTimestamp": "2024-01-15T09:00:00Z",
    "pendingChanges": 5
  }
}
```

## Account Management Integration

### Android AccountManager Integration

```kotlin
class LogDateAccountManager(private val context: Context) {
    
    fun addAccount(accountData: AccountCreationResponse) {
        val account = Account(
            accountData.account.username,
            "app.logdate.account"
        )
        
        val accountManager = AccountManager.get(context)
        val userData = Bundle().apply {
            putString("userId", accountData.account.userId)
            putString("displayName", accountData.account.displayName)
            putString("serverEndpoint", accountData.syncData.serverEndpoint)
            putString("credentialId", accountData.passkey.credentialId)
        }
        
        accountManager.addAccountExplicitly(account, null, userData)
        accountManager.setAuthToken(
            account, 
            "access_token", 
            accountData.session.accessToken
        )
        accountManager.setAuthToken(
            account,
            "refresh_token", 
            accountData.session.refreshToken
        )
    }
    
    fun getStoredAccounts(): List<Account> {
        val accountManager = AccountManager.get(context)
        return accountManager.getAccountsByType("app.logdate.account").toList()
    }
    
    fun selectAccount(onAccountSelected: (Account) -> Unit) {
        // Use AccountPicker to let user choose between multiple accounts
        val intent = AccountPicker.newChooseAccountIntent(
            null, null,
            arrayOf("app.logdate.account"),
            true, null, null, null, null
        )
        // Handle intent result in activity
    }
}
```

### iOS Keychain Integration

```swift
class LogDateKeychainManager {
    private let service = "app.logdate.account"
    
    func storeAccount(_ accountData: AccountCreationResponse) {
        let accountInfo = [
            "userId": accountData.account.userId,
            "username": accountData.account.username,
            "displayName": accountData.account.displayName,
            "credentialId": accountData.passkey.credentialId,
            "serverEndpoint": accountData.syncData.serverEndpoint
        ]
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: accountData.account.username,
            kSecValueData as String: try! JSONSerialization.data(withJSONObject: accountInfo)
        ]
        
        SecItemAdd(query as CFDictionary, nil)
        
        // Store tokens separately with high security
        storeToken(accountData.session.accessToken, type: "access_token", account: accountData.account.username)
        storeToken(accountData.session.refreshToken, type: "refresh_token", account: accountData.account.username)
    }
    
    private func storeToken(_ token: String, type: String, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "\(service).\(type)",
            kSecAttrAccount as String: account,
            kSecValueData as String: token.data(using: .utf8)!,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        
        SecItemAdd(query as CFDictionary, nil)
    }
}
```

## Server-Side Account Management

### Account Creation Logic

```kotlin
class PasskeyAccountService {
    
    suspend fun beginAccountCreation(request: BeginAccountCreationRequest): BeginAccountCreationResponse {
        // Generate temporary user ID and challenge
        val temporaryUserId = UUID.randomUUID()
        val challenge = generateSecureChallenge()
        val sessionId = UUID.randomUUID().toString()
        
        // Generate username if not provided
        val username = request.preferredUsername 
            ?: generateUsername(request.deviceInfo?.deviceName)
        
        // Store temporary session
        sessionStorage.store(sessionId, TemporarySession(
            temporaryUserId = temporaryUserId,
            challenge = challenge,
            username = username,
            deviceInfo = request.deviceInfo,
            expiresAt = Clock.System.now().plus(5.minutes)
        ))
        
        return BeginAccountCreationResponse(
            challenge = challenge.toBase64Url(),
            userId = temporaryUserId.toByteArray().toBase64Url(),
            sessionId = sessionId,
            registrationOptions = buildRegistrationOptions(temporaryUserId, username, challenge)
        )
    }
    
    suspend fun completeAccountCreation(request: CompleteAccountCreationRequest): AccountCreationResponse {
        // Verify session and challenge
        val session = sessionStorage.get(request.sessionId)
            ?: throw InvalidSessionException()
        
        // Verify WebAuthn credential
        val verificationResult = webAuthnVerifier.verifyRegistration(
            request.credential,
            session.challenge,
            origin = "https://app.logdate.app"
        )
        
        if (!verificationResult.isValid) {
            throw PasskeyVerificationException(verificationResult.error)
        }
        
        // Create permanent account
        val account = Account(
            id = UUID.randomUUID(),
            username = session.username,
            displayName = request.accountPreferences.displayName ?: session.username,
            createdAt = Clock.System.now(),
            timezone = request.accountPreferences.timezone,
            locale = request.accountPreferences.locale
        )
        
        // Store account
        accountRepository.save(account)
        
        // Store passkey
        val passkey = Passkey(
            id = UUID.randomUUID(),
            accountId = account.id,
            credentialId = request.credential.id,
            publicKey = verificationResult.publicKey,
            signCount = verificationResult.signCount,
            deviceInfo = session.deviceInfo,
            createdAt = Clock.System.now()
        )
        
        passkeyRepository.save(passkey)
        
        // Generate session tokens
        val tokens = tokenService.generateTokens(account.id)
        
        // Clean up temporary session
        sessionStorage.remove(request.sessionId)
        
        return AccountCreationResponse(
            account = account.toAccountInfo(),
            session = tokens,
            passkey = passkey.toPasskeyInfo(),
            syncData = SyncData(
                serverEndpoint = config.apiEndpoint,
                initialSyncRequired = true
            )
        )
    }
    
    private fun generateUsername(deviceName: String?): String {
        val base = deviceName?.let { name ->
            // Extract meaningful part from device name
            name.replace(Regex("[^a-zA-Z0-9]"), "")
                .take(10)
                .lowercase()
        } ?: "user"
        
        val suffix = Random.nextInt(1000, 9999)
        return "${base}${suffix}"
    }
}
```

## Security Considerations

### Challenge Generation
- Challenges must be cryptographically secure random values (32+ bytes)
- Challenges expire after 5 minutes maximum
- Each challenge can only be used once
- Challenges are bound to the session and user context

### Account Security
- User verification (biometrics/PIN) is required for all operations
- Resident keys are required to enable discoverable credentials
- Backup eligible/backup state flags are tracked for account recovery scenarios
- Sign count is monitored to detect credential cloning attempts

### Session Management
- Access tokens expire after 1 hour
- Refresh tokens expire after 30 days
- Token rotation on each refresh
- Device-bound sessions prevent token reuse across devices

### Privacy Protection
- No email addresses required or stored unless explicitly provided
- Usernames are generated automatically if not specified
- Account data is encrypted at rest
- Audit logs track all authentication events

## Error Handling

### Client-Side Error Codes
```kotlin
enum class PasskeyErrorCode {
    NOT_SUPPORTED,           // Platform doesn't support passkeys
    USER_CANCELLED,          // User cancelled the operation
    SECURITY_ERROR,          // Device security not available
    NETWORK_ERROR,           // Network connectivity issues
    SERVER_ERROR,            // Server-side error
    INVALID_CHALLENGE,       // Challenge validation failed
    ACCOUNT_EXISTS,          // Account already exists for this credential
    CREDENTIAL_NOT_FOUND,    // No matching credential found
    TIMEOUT                  // Operation timed out
}
```

### Server-Side Error Responses
```json
{
  "error": {
    "code": "PASSKEY_VERIFICATION_FAILED",
    "message": "The passkey credential could not be verified",
    "details": {
      "reason": "invalid_signature",
      "challengeExpired": false,
      "retryAllowed": true
    },
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

## Platform-Specific Implementation Notes

### Android
- Requires Android 9+ with Google Play Services
- Uses `androidx.credentials.CredentialManager`
- Integrates with `AccountManager` for system-level account storage
- Supports both platform and cross-platform authenticators

### iOS
- Requires iOS 16+ for full passkey support
- Uses `AuthenticationServices.ASAuthorizationController`
- Integrates with Keychain for secure token storage
- Platform authenticator (Face ID/Touch ID) preferred

### Web
- Uses standard WebAuthn JavaScript APIs
- Falls back to FIDO2 security keys if platform authenticator unavailable
- Session storage in secure HTTP-only cookies
- Cross-origin considerations for subdomain usage

## Migration and Backup

### Adding Passkeys to Existing Accounts
Users with traditional email/password accounts can add passkeys:

```
POST /api/v1/auth/passkey/add
Authorization: Bearer <existing_session_token>
{
  "nickname": "iPhone Passkey",
  "isPrimary": false
}
```

### Account Recovery
- Multiple passkeys can be registered per account
- Account recovery codes provided during setup
- Administrative recovery process for enterprise accounts
- Passkey revocation and replacement procedures

## Compliance and Standards

### WebAuthn Compliance
- Full Level 2 WebAuthn specification compliance
- FIDO2 Client to Authenticator Protocol (CTAP) support
- W3C Web Authentication API compatibility

### Platform Integration
- Android: Google Identity Services integration
- iOS: Apple Sign In compatibility
- Web: Credential Management API support

This specification ensures LogDate Cloud provides a secure, user-friendly, and standards-compliant passkey authentication experience across all supported platforms.