# LogDate Server Environment Variables

**Authoritative reference for all environment variables used by the LogDate server.**

> Last updated: 2026-03-07

---

## Table of Contents

- [Server Configuration](#server-configuration)
- [Database Configuration](#database-configuration)
- [Authentication & Security](#authentication--security)
- [Encryption](#encryption)
- [Media Storage](#media-storage)
- [Sync & Maintenance](#sync--maintenance)
- [Redis (Optional)](#redis-optional)

---

## Server Configuration

### `PORT`
- **Description**: Server HTTP port
- **Type**: Integer
- **Default**: `8080`
- **Example**: `PORT=3000`
- **Required**: No

### `HOST`
- **Description**: Server bind address
- **Type**: String
- **Default**: `0.0.0.0` (all interfaces)
- **Example**: `HOST=127.0.0.1`
- **Required**: No

---

## Database Configuration

The server supports two sets of database environment variables for flexibility:

### Primary Database Variables

### `DATABASE_URL`
- **Description**: Full PostgreSQL connection URL
- **Type**: String (JDBC URL)
- **Default**: None
- **Example**: `jdbc:postgresql://localhost:5432/logdate`
- **Required**: Yes (if database is enabled)

### `DATABASE_USER`
- **Description**: PostgreSQL username
- **Type**: String
- **Default**: None
- **Example**: `DATABASE_USER=logdate_user`
- **Required**: Yes (if `DATABASE_URL` is set)

### `DATABASE_PASSWORD`
- **Description**: PostgreSQL password
- **Type**: String
- **Default**: None
- **Example**: `DATABASE_PASSWORD=secure_password_here`
- **Required**: Yes (if `DATABASE_URL` is set)
- **Security**: Store securely, never commit to version control

### Alternative Database Variables

These can be used instead of `DATABASE_URL`:

### `DB_HOST`
- **Description**: PostgreSQL host address
- **Type**: String
- **Default**: `localhost`
- **Example**: `DB_HOST=db.example.com`
- **Required**: No

### `DB_PORT`
- **Description**: PostgreSQL port
- **Type**: Integer
- **Default**: `5432`
- **Example**: `DB_PORT=5433`
- **Required**: No

### `DB_NAME`
- **Description**: PostgreSQL database name
- **Type**: String
- **Default**: `logdate`
- **Example**: `DB_NAME=logdate_prod`
- **Required**: No

### `DB_USER`
- **Description**: PostgreSQL username (alternative to `DATABASE_USER`)
- **Type**: String
- **Default**: None
- **Example**: `DB_USER=postgres`
- **Required**: No

### `DB_PASSWORD`
- **Description**: PostgreSQL password (alternative to `DATABASE_PASSWORD`)
- **Type**: String
- **Default**: None
- **Example**: `DB_PASSWORD=password123`
- **Required**: No
- **Security**: Store securely, never commit to version control

---

## Authentication & Security

### `JWT_SECRET`
- **Description**: Secret key for JWT token signing
- **Type**: String (minimum 32 characters recommended)
- **Default**: None
- **Example**: `JWT_SECRET=your-super-secret-jwt-signing-key-here-min-32-chars`
- **Required**: Yes (for production)
- **Security**: 
  - Must be at least 32 characters for security
  - Store securely, never commit to version control
  - Rotate periodically in production

### `GOOGLE_OIDC_CLIENT_IDS`
- **Description**: Comma-separated Google OAuth client IDs accepted for ID token verification
- **Type**: String (CSV)
- **Default**: None
- **Example**: `GOOGLE_OIDC_CLIENT_IDS=123.apps.googleusercontent.com,456.apps.googleusercontent.com`
- **Required**: Yes (if Google sign-in is enabled)
- **Security**:
  - Keep aligned with released mobile/web client IDs only
  - Rotate/remove deprecated client IDs promptly
  - Prefer storing in secret manager for production deployments

### `WEBAUTHN_RP_ID`
- **Description**: WebAuthn relying party ID used for passkey ceremonies
- **Type**: String
- **Default**: `logdate.app`
- **Example**: `WEBAUTHN_RP_ID=app.logdate.com`
- **Required**: Yes (for production passkeys)

### `WEBAUTHN_RP_NAME`
- **Description**: Display name presented as relying party name in passkey prompts
- **Type**: String
- **Default**: `LogDate`
- **Example**: `WEBAUTHN_RP_NAME=LogDate Cloud`
- **Required**: No

### `WEBAUTHN_ORIGIN`
- **Description**: Expected WebAuthn origin for registration/authentication responses
- **Type**: String (HTTPS origin)
- **Default**: `https://app.logdate.com`
- **Example**: `WEBAUTHN_ORIGIN=https://app.logdate.com`
- **Required**: Yes (for production passkeys)

### `WEBAUTHN_STRICT_VERIFICATION`
- **Description**: Enables strict WebAuthn4J cryptographic verification for passkeys
- **Type**: Boolean
- **Default**: `false`
- **Example**: `WEBAUTHN_STRICT_VERIFICATION=true`
- **Required**: **Yes for production**
- **Notes**:
  - Strict mode requires valid Base64URL WebAuthn payloads from clients
  - Keep disabled only for local/test environments

---

## Encryption

### `SERVER_ENCRYPTION_KEY`
- **Description**: Base64-encoded AES encryption key for server-side encryption
- **Type**: String (Base64)
- **Default**: None
- **Example**: `SERVER_ENCRYPTION_KEY=YourBase64EncodedKeyHere==`
- **Required**: No (but recommended for production)
- **Security**:
  - Must decode to 16, 24, or 32 bytes (AES-128/192/256)
  - Store in secret manager (e.g., GCP Secret Manager, AWS Secrets Manager)
  - Never commit to version control
  - Generate with: `openssl rand -base64 32`
- **Notes**: If not set, encryption service uses NoOpKeyring (test mode)

### `SERVER_ENCRYPTION_KEY_ID`
- **Description**: Identifier for the active encryption key (enables key rotation)
- **Type**: String
- **Default**: `default`
- **Example**: `SERVER_ENCRYPTION_KEY_ID=key-2026-01`
- **Required**: No
- **Notes**: Change this when rotating keys to track which key encrypted each payload

### `ENCRYPTION_MODE`
- **Description**: Server encryption policy mode
- **Type**: Enum
- **Values**:
  - `AT_REST_ONLY` - Server encrypts all data, accepts client ciphertext (default)
  - `E2EE_REQUIRED` - Only accepts client-encrypted data, rejects plaintext
- **Default**: `AT_REST_ONLY`
- **Example**: `ENCRYPTION_MODE=E2EE_REQUIRED`
- **Required**: No

### `SERVER_ENCRYPTION_ENABLED`
- **Description**: Enable/disable server-side encryption
- **Type**: Boolean
- **Default**: `true` (if `SERVER_ENCRYPTION_KEY` is set)
- **Example**: `SERVER_ENCRYPTION_ENABLED=false`
- **Required**: No
- **Notes**: Setting to `false` disables encryption even if key is configured

### `ALLOW_PASSTHROUGH_CLIENT_CIPHERTEXT`
- **Description**: Allow client-encrypted payloads to pass through without server re-encryption
- **Type**: Boolean
- **Default**: `true`
- **Example**: `ALLOW_PASSTHROUGH_CLIENT_CIPHERTEXT=false`
- **Required**: No
- **Notes**: Disable this to force server-side re-encryption of all uploads

---

## Media Storage

### `GCS_PROJECT_ID`
- **Description**: Google Cloud Storage project ID
- **Type**: String
- **Default**: None
- **Example**: `GCS_PROJECT_ID=my-project-123456`
- **Required**: Yes (if using GCS)

### `GCS_BUCKET_NAME`
- **Description**: GCS bucket name for media storage
- **Type**: String
- **Default**: None
- **Example**: `GCS_BUCKET_NAME=logdate-media-prod`
- **Required**: Yes (if using GCS)

### `GCS_MEDIA_KMS_KEY`
- **Description**: GCS KMS key for media encryption at rest (Google-managed)
- **Type**: String (KMS key resource name)
- **Default**: None
- **Example**: `GCS_MEDIA_KMS_KEY=projects/my-project/locations/us/keyRings/my-ring/cryptoKeys/media-key`
- **Required**: No
- **Notes**: Separate from `SERVER_ENCRYPTION_KEY` - this is for GCS-level encryption

### `SYNC_MEDIA_SIGNED_URL_TTL_HOURS`
- **Description**: TTL for GCS signed URLs (hours)
- **Type**: Integer
- **Default**: `1`
- **Range**: 1-24
- **Example**: `SYNC_MEDIA_SIGNED_URL_TTL_HOURS=6`
- **Required**: No

---

## Sync & Maintenance

### `SYNC_TOMBSTONE_PURGE_ENABLED`
- **Description**: Enable automatic purging of old deletion markers (tombstones)
- **Type**: Boolean
- **Default**: `true`
- **Example**: `SYNC_TOMBSTONE_PURGE_ENABLED=false`
- **Required**: No
- **Notes**: Disable for debugging or to manually control purge operations

### `SYNC_TOMBSTONE_RETENTION_DAYS`
- **Description**: How many days to keep deletion markers before purging
- **Type**: Integer
- **Default**: `30`
- **Range**: 1-3650 (1 day to 10 years)
- **Example**: `SYNC_TOMBSTONE_RETENTION_DAYS=90`
- **Required**: No
- **Notes**: Longer retention = more storage, but better sync reliability for offline devices

### `SYNC_TOMBSTONE_PURGE_INTERVAL_HOURS`
- **Description**: How often to run the tombstone purge job (hours)
- **Type**: Integer
- **Default**: `24`
- **Minimum**: 1
- **Example**: `SYNC_TOMBSTONE_PURGE_INTERVAL_HOURS=12`
- **Required**: No
- **Notes**: More frequent purges = lower storage, but higher database load

---

## Redis (Optional)

### `REDIS_URL`
- **Description**: Redis connection URL for caching/session storage
- **Type**: String (Redis URL)
- **Default**: None
- **Example**: `REDIS_URL=redis://localhost:6379`
- **Required**: No
- **Notes**: Currently not actively used, reserved for future caching features

---

## Configuration Examples

### Minimal Development Setup

```bash
# Server
PORT=8080
HOST=0.0.0.0

# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/logdate_dev
DATABASE_USER=postgres
DATABASE_PASSWORD=password

# Auth
JWT_SECRET=dev-secret-key-not-for-production-min-32-chars
```

### Production Setup (Google Cloud)

```bash
# Server
PORT=8080
HOST=0.0.0.0

# Database (Cloud SQL)
DATABASE_URL=jdbc:postgresql://10.1.2.3:5432/logdate_prod
DATABASE_USER=logdate_user
DATABASE_PASSWORD=${DB_PASSWORD_FROM_SECRET_MANAGER}

# Auth
JWT_SECRET=${JWT_SECRET_FROM_SECRET_MANAGER}

# Encryption
SERVER_ENCRYPTION_KEY=${ENCRYPTION_KEY_FROM_SECRET_MANAGER}
SERVER_ENCRYPTION_KEY_ID=prod-key-2026-01
ENCRYPTION_MODE=AT_REST_ONLY
SERVER_ENCRYPTION_ENABLED=true
ALLOW_PASSTHROUGH_CLIENT_CIPHERTEXT=true

# GCS
GCS_PROJECT_ID=logdate-prod
GCS_BUCKET_NAME=logdate-media-prod
GCS_MEDIA_KMS_KEY=projects/logdate-prod/locations/us/keyRings/media/cryptoKeys/media-encryption
SYNC_MEDIA_SIGNED_URL_TTL_HOURS=1

# Maintenance
SYNC_TOMBSTONE_PURGE_ENABLED=true
SYNC_TOMBSTONE_RETENTION_DAYS=30
SYNC_TOMBSTONE_PURGE_INTERVAL_HOURS=24
```

### E2EE Mode (End-to-End Encryption)

```bash
# All production settings above, plus:
ENCRYPTION_MODE=E2EE_REQUIRED
ALLOW_PASSTHROUGH_CLIENT_CIPHERTEXT=true

# Note: This rejects plaintext uploads, requires clients to encrypt
```

---

## Security Best Practices

1. **Never commit secrets to version control**
   - Use `.env` files (add to `.gitignore`)
   - Use secret managers (GCP Secret Manager, AWS Secrets Manager, etc.)
   - Use environment variables in CI/CD pipelines

2. **Rotate secrets periodically**
   - `JWT_SECRET`: Rotate quarterly
   - `SERVER_ENCRYPTION_KEY`: Support multiple keys via key rotation (increment `SERVER_ENCRYPTION_KEY_ID`)
   - Database passwords: Rotate annually or after security incidents

3. **Use strong values**
   - `JWT_SECRET`: Minimum 32 characters, random
   - `SERVER_ENCRYPTION_KEY`: Use `openssl rand -base64 32` for AES-256
   - Database passwords: 16+ characters, random

4. **Validate in production**
   - Ensure all required variables are set
   - Check secret manager integration
   - Test encryption/decryption before deploying

5. **Monitor access**
   - Log failed authentication attempts
   - Monitor encryption errors
   - Alert on unusual database access patterns

---

## Troubleshooting

### Common Issues

**"SERVER_ENCRYPTION_KEY not configured"**
- Set `SERVER_ENCRYPTION_KEY` environment variable
- Or disable encryption with `SERVER_ENCRYPTION_ENABLED=false` (not recommended for production)

**"SERVER_ENCRYPTION_KEY must be base64-encoded"**
- Ensure key is valid base64
- Generate new key: `openssl rand -base64 32`

**"SERVER_ENCRYPTION_KEY must decode to 16, 24, or 32 bytes"**
- Key must be exactly 128, 192, or 256 bits
- Use AES-256 (32 bytes): `openssl rand -base64 32`

**Database connection fails**
- Verify `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` are correct
- Check database server is running and accessible
- Verify firewall rules allow connection

**GCS access denied**
- Verify service account has Storage Object Admin role
- Check `GCS_PROJECT_ID` and `GCS_BUCKET_NAME` are correct
- Ensure Application Default Credentials are configured

---

## See Also

- [Google Cloud Production Setup](./google-cloud-production.md)
- [Google Cloud Architecture](./google-cloud-architecture.md)
- [Auth V1 API](./auth-v1-api.md)
- [Sync V1 API](./sync-v1-api.md)
- [Encryption Architecture Design](../docs/plans/2026-01-27-encryption-architecture-overhaul-design.md)
