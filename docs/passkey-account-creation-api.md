# Passkey Account Creation API Documentation

## Overview

This document describes the RESTful API endpoints for creating LogDate Cloud accounts using passkeys. The implementation follows WebAuthn standards and provides a complete email-optional account creation flow.

## Base URL

```
https://api.logdate.app/api/v1
```

## Authentication

- **Account Creation**: No authentication required (public endpoints)
- **Account Management**: Bearer token authentication required
- **Token Format**: `Authorization: Bearer <jwt_access_token>`

## Account Creation Flow

### 1. Begin Account Creation

Initiates the account creation process and returns passkey registration options.

**Endpoint:** `POST /accounts/create/begin`

**Request Body:**
```json
{
  "username": "johndoe123",
  "displayName": "John Doe",
  "bio": "Optional user bio"
}
```

**Request Schema:**
- `username` (string, required): Desired username (3-50 characters, alphanumeric + underscore)
- `displayName` (string, required): User's display name (1-100 characters)
- `bio` (string, optional): User biography (max 500 characters)

**Success Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "sessionToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "registrationOptions": {
      "challenge": "dGVzdGNoYWxsZW5nZQ",
      "rp": {
        "id": "logdate.app",
        "name": "LogDate Cloud"
      },
      "user": {
        "id": "dXNlcjEyMw",
        "name": "johndoe123",
        "displayName": "John Doe"
      },
      "pubKeyCredParams": [
        {"type": "public-key", "alg": -7},
        {"type": "public-key", "alg": -257}
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
}
```

**Error Responses:**

- `409 Conflict` - Username already taken
```json
{
  "error": {
    "code": "USERNAME_TAKEN",
    "message": "Username 'johndoe123' is already taken"
  }
}
```

- `400 Bad Request` - Invalid request data
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Username must be 3-50 characters long"
  }
}
```

### 2. Complete Account Creation

Completes the account creation process using the passkey credential from the client.

**Endpoint:** `POST /accounts/create/complete`

**Request Body:**
```json
{
  "sessionToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "credential": {
    "id": "credentialId123",
    "rawId": "Y3JlZGVudGlhbElkMTIz",
    "response": {
      "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoidGVzdGNoYWxsZW5nZSIsIm9yaWdpbiI6Imh0dHBzOi8vbG9nZGF0ZS5hcHAifQ",
      "attestationObject": "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YVikSZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2NBAAAAAAAAAAAAAAAAAAAAAAAAFGNyZWRlbnRpYWxJZDEyM6UBAgMmIAEhWCBkZzAyN..."
    },
    "type": "public-key"
  }
}
```

**Request Schema:**
- `sessionToken` (string, required): Session token from begin endpoint
- `credential` (object, required): WebAuthn credential response
  - `id` (string): Credential ID
  - `rawId` (string): Base64url encoded raw credential ID
  - `response` (object): Authenticator response
    - `clientDataJSON` (string): Base64url encoded client data
    - `attestationObject` (string): Base64url encoded attestation object
  - `type` (string): Must be "public-key"

**Success Response:** `201 Created`
```json
{
  "success": true,
  "data": {
    "account": {
      "id": "acc_1234567890",
      "username": "johndoe123",
      "displayName": "John Doe",
      "bio": null,
      "passkeyCredentialIds": ["credentialId123"],
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z"
    },
    "tokens": {
      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
  }
}
```

**Error Responses:**

- `401 Unauthorized` - Invalid session token
```json
{
  "error": {
    "code": "INVALID_SESSION_TOKEN",
    "message": "Session token is invalid or expired"
  }
}
```

- `400 Bad Request` - Passkey verification failed
```json
{
  "error": {
    "code": "PASSKEY_VERIFICATION_FAILED",
    "message": "Passkey verification failed"
  }
}
```

## Account Authentication Flow

### 1. Begin Authentication

Initiates authentication for an existing account.

**Endpoint:** `POST /accounts/authenticate/begin`

**Request Body:**
```json
{
  "username": "johndoe123"
}
```

**Request Schema:**
- `username` (string, optional): Username hint for credential selection

**Success Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "challenge": "YXV0aGVudGljYXRpb25jaGFsbGVuZ2U",
    "rpId": "logdate.app",
    "allowCredentials": [
      {
        "type": "public-key",
        "id": "Y3JlZGVudGlhbElkMTIz",
        "transports": ["internal", "hybrid"]
      }
    ],
    "timeout": 300000,
    "userVerification": "required"
  }
}
```

### 2. Complete Authentication

Completes authentication using passkey assertion.

**Endpoint:** `POST /accounts/authenticate/complete`

**Request Body:**
```json
{
  "credential": {
    "id": "credentialId123",
    "rawId": "Y3JlZGVudGlhbElkMTIz",
    "response": {
      "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0IiwiY2hhbGxlbmdlIjoiWVhWMGFHVnVkR2xqWVhScGIyNWphR0ZzYkdWdVoyVSIsIm9yaWdpbiI6Imh0dHBzOi8vbG9nZGF0ZS5hcHAifQ",
      "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4_krrmihjLHmVzzuoMdl2MBAAAABQ",
      "signature": "MEUCIQDZlMhLo...",
      "userHandle": "dXNlcjEyMw"
    },
    "type": "public-key"
  },
  "challenge": "YXV0aGVudGljYXRpb25jaGFsbGVuZ2U"
}
```

**Success Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "account": {
      "id": "acc_1234567890",
      "username": "johndoe123",
      "displayName": "John Doe",
      "bio": null,
      "passkeyCredentialIds": ["credentialId123"],
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z"
    },
    "tokens": {
      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
  }
}
```

## Account Management

### Refresh Access Token

Obtain a new access token using a refresh token.

**Endpoint:** `POST /accounts/refresh`

**Request Body:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Success Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### Get Account Information

Retrieve current account information (requires authentication).

**Endpoint:** `GET /accounts/me`

**Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Success Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "id": "acc_1234567890",
    "username": "johndoe123",
    "displayName": "John Doe",
    "bio": null,
    "passkeyCredentialIds": ["credentialId123"],
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
}
```

### Check Username Availability

Check if a username is available for registration.

**Endpoint:** `GET /accounts/username/{username}/available`

**Path Parameters:**
- `username` (string): Username to check

**Success Response:** `200 OK`
```json
{
  "success": true,
  "data": {
    "username": "johndoe123",
    "available": false
  }
}
```

## Error Handling

### Standard Error Response Format

All error responses follow this format:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message"
  }
}
```

### Common Error Codes

- `VALIDATION_ERROR`: Request validation failed
- `USERNAME_TAKEN`: Username already exists
- `INVALID_SESSION_TOKEN`: Session token invalid or expired
- `INVALID_TOKEN`: Access token invalid or expired
- `INVALID_REFRESH_TOKEN`: Refresh token invalid or expired
- `PASSKEY_VERIFICATION_FAILED`: Passkey credential verification failed
- `ACCOUNT_NOT_FOUND`: Account not found
- `SESSION_NOT_FOUND`: Session not found or expired
- `AUTHENTICATION_FAILED`: Authentication failed

### HTTP Status Codes

- `200 OK`: Successful request
- `201 Created`: Resource created successfully
- `400 Bad Request`: Invalid request data
- `401 Unauthorized`: Authentication required or failed
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource conflict (e.g., username taken)
- `500 Internal Server Error`: Server error

## Rate Limiting

- **Account Creation**: 5 attempts per IP per hour
- **Authentication**: 10 attempts per IP per minute
- **Token Refresh**: 100 requests per account per hour
- **Username Check**: 50 requests per IP per minute

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 1642249200
```

## Security Considerations

### HTTPS Only
All endpoints must be accessed over HTTPS in production.

### CORS Policy
The API supports cross-origin requests from:
- `https://app.logdate.app`
- `https://web.logdate.app`
- Native mobile apps (no origin restrictions)

### Content Security Policy
```
Content-Security-Policy: default-src 'self'; connect-src 'self' https://api.logdate.app
```

### Request Validation
- All input is validated and sanitized
- Maximum request body size: 1MB
- Request timeout: 30 seconds

### Audit Logging
All account creation and authentication events are logged with:
- Timestamp
- IP address
- User agent
- Account ID (for authenticated requests)
- Success/failure status