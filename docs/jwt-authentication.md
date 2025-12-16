# JWT Authentication System Documentation

## Overview

LogDate Cloud uses JSON Web Tokens (JWT) for session management and API authentication. This document describes the token types, lifecycle, and implementation details.

## Token Types

### 1. Access Tokens
- **Purpose**: Authenticate API requests
- **Lifetime**: 1 hour (3600 seconds)
- **Usage**: Include in `Authorization: Bearer <token>` header
- **Claims**: 
  - `sub`: Account ID
  - `type`: "access"
  - `iat`: Issued at timestamp
  - `exp`: Expiration timestamp

### 2. Refresh Tokens
- **Purpose**: Obtain new access tokens
- **Lifetime**: 30 days (2,592,000 seconds)
- **Usage**: POST to `/api/v1/accounts/refresh` endpoint
- **Claims**:
  - `sub`: Account ID
  - `type`: "refresh"
  - `iat`: Issued at timestamp
  - `exp`: Expiration timestamp

### 3. Session Tokens
- **Purpose**: Temporary tokens during account creation flow
- **Lifetime**: 15 minutes (900 seconds)
- **Usage**: Include in account creation complete request
- **Claims**:
  - `sub`: Session ID
  - `type`: "session"
  - `iat`: Issued at timestamp
  - `exp`: Expiration timestamp

## Token Security

### Signing Algorithm
- **Algorithm**: HS256 (HMAC with SHA-256)
- **Key**: Configurable secret (minimum 256 bits)
- **Default**: Development key (must be changed in production)

### Security Best Practices
1. **Secret Key Management**: Use environment variables or secure configuration
2. **Token Rotation**: Refresh tokens before expiration
3. **Secure Storage**: Store tokens in platform-specific secure storage
4. **Transport Security**: Always use HTTPS in production

## API Endpoints

### Token Refresh
```http
POST /api/v1/accounts/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### Protected Endpoint Example
```http
GET /api/v1/accounts/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## Implementation Details

### TokenService Interface
```kotlin
interface TokenService {
    fun generateAccessToken(accountId: String): String
    fun generateRefreshToken(accountId: String): String
    fun generateSessionToken(sessionId: String): String
    
    fun validateAccessToken(token: String): String?
    fun validateRefreshToken(token: String): String?
    fun validateSessionToken(token: String): String?
}
```

### KotlinTokenService Implementation
- Uses **jwt-kt** Kotlin multiplatform library for token operations
- Pure Kotlin implementation with no Java dependencies
- HMAC SHA-256 algorithm for signing
- Configurable JWT secret key and issuer
- Automatic token expiration handling using Kotlin Duration
- Type-safe token validation with comprehensive error handling

### Library Choice: jwt-kt
We chose the `jwt-kt` library by Appstractive because:
- **Kotlin-native**: Written specifically for Kotlin with idiomatic APIs
- **Multiplatform**: Supports JVM, Android, iOS, JS, and native platforms
- **Modern**: Uses Kotlin coroutines, Duration, and other modern Kotlin features
- **Lightweight**: No heavy Java dependencies
- **Actively maintained**: Regular updates and community support

### Error Handling
- Invalid tokens return `null` from validation methods
- Expired tokens are automatically rejected
- Type mismatches (e.g., using refresh token as access token) are rejected
- Malformed tokens are safely handled

## Client Integration

### Android Example
```kotlin
class TokenManager(private val context: Context) {
    private val keyAlias = "logdate_tokens"
    
    fun storeTokens(accessToken: String, refreshToken: String) {
        val encryptedSharedPrefs = EncryptedSharedPreferences.create(
            "secure_prefs",
            keyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        encryptedSharedPrefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }
    
    fun getAccessToken(): String? {
        return encryptedSharedPrefs.getString("access_token", null)
    }
}
```

### iOS Example
```swift
class TokenManager {
    private let service = "app.logdate.tokens"
    
    func storeTokens(accessToken: String, refreshToken: String) {
        storeToken(accessToken, key: "access_token")
        storeToken(refreshToken, key: "refresh_token")
    }
    
    private func storeToken(_ token: String, key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecValueData as String: token.data(using: .utf8)!,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        
        SecItemAdd(query as CFDictionary, nil)
    }
}
```

## Production Configuration

### Environment Variables
```bash
# JWT Secret (minimum 32 characters)
JWT_SECRET=your-super-secure-secret-key-here-min-256-bits

# Token Expiration (optional, defaults shown)
ACCESS_TOKEN_EXPIRATION=3600
REFRESH_TOKEN_EXPIRATION=2592000
SESSION_TOKEN_EXPIRATION=900
```

### Docker Configuration
```dockerfile
ENV JWT_SECRET=${JWT_SECRET}
ENV ACCESS_TOKEN_EXPIRATION=3600
ENV REFRESH_TOKEN_EXPIRATION=2592000
```

## Monitoring and Logging

### Security Events to Log
- Token generation (with account ID, not token value)
- Token validation failures
- Refresh token usage
- Token expiration events

### Metrics to Track
- Token generation rate
- Token validation success/failure rate
- Average token lifetime usage
- Refresh frequency patterns

## Troubleshooting

### Common Issues

1. **"Invalid token" errors**
   - Check token format (3 parts separated by dots)
   - Verify Authorization header format: `Bearer <token>`
   - Ensure token hasn't expired

2. **Token validation failures**
   - Verify JWT secret key matches between generation and validation
   - Check system clock synchronization
   - Ensure token type matches endpoint requirements

3. **Refresh token issues**
   - Verify refresh token hasn't expired (30 days)
   - Check that account still exists
   - Ensure proper token storage on client

### Usage Example
```kotlin
// Initialize the service
val tokenService = KotlinTokenService(
    jwtSecret = "your-secret-key-here",
    issuer = "logdate.app"
)

// Generate tokens
val accessToken = tokenService.generateAccessToken("account-123")
val refreshToken = tokenService.generateRefreshToken("account-123")

// Validate tokens
val accountId = tokenService.validateAccessToken(accessToken)
// Returns "account-123" or null if invalid
```

### Kotlin Multiplatform Benefits
The jwt-kt library allows the same TokenService interface to be used across:
- **Server (JVM)**: For API authentication
- **Android**: For client-side token handling
- **iOS**: For client-side token handling  
- **Desktop**: For desktop app authentication
- **Web**: For web client authentication

This provides a consistent API across all platforms while maintaining platform-specific optimizations and security features.