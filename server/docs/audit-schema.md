# Audit Schema (Centralized)

This document is the centralized reference for server audit categories and keys.

## Source of Truth

- Code constants: `server/src/main/kotlin/app/logdate/server/audit/AuditLogSchema.kt`
- Log format: `audit.<category> key=value key=value ...`

## Categories

### `auth.signup.passkey.success`
Emitted when passkey signup completes and account is created.

Expected keys:
- `accountId`
- `ipHash`
- `userAgentHash`

### `auth.signup.google.success`
Emitted when Google signup completes successfully.

Expected keys:
- `accountId`
- `ipHash`
- `userAgentHash`

### `auth.signin.passkey.success`
Emitted when passkey signin completes successfully.

Expected keys:
- `accountId`
- `credentialIdHash`
- `ipHash`
- `userAgentHash`

### `auth.signin.google.success`
Emitted when Google signin completes successfully.

Expected keys:
- `accountId`
- `ipHash`
- `userAgentHash`

### `auth.link.google.implicit`
Emitted when Google identity is linked implicitly via verified-email match.

Expected keys:
- `accountId`
- `providerSubjectHash`
- `ipHash`
- `userAgentHash`

## Key Registry

- `accountId`: Account UUID.
- `ipHash`: SHA-256 hash of remote IP.
- `userAgentHash`: SHA-256 hash of request User-Agent.
- `credentialIdHash`: SHA-256 hash of passkey credential ID.
- `providerSubjectHash`: SHA-256 hash of social provider subject.

## Conventions

- Never log raw tokens, raw IP addresses, raw User-Agent strings, or raw provider subjects.
- Add new categories/keys to `AuditLogSchema.kt` first, then update this document in the same change.
