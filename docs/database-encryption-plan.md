# Database Encryption Plan (Draft)

## Goal
Encrypt local LogDate databases at rest on all supported platforms, with keys stored in platform-secure storage.

## Scope
- Android: Room + SQLCipher (or equivalent) with key stored in Android Keystore.
- iOS: Encrypted SQLite (SQLCipher or platform-backed file protection) with key in Keychain.
- Desktop: SQLCipher or AES-encrypted SQLite with key stored in secure storage file.

## Key management
- Keys are generated per device and stored in SecureStorage.
- Keys are never logged or serialized in plaintext.
- Key rotation is supported by rekeying the database file.

## Migration strategy
1) Introduce encrypted database driver behind a feature flag.
2) On first run with flag enabled:
   - Create encrypted DB with new key.
   - Migrate data from plaintext DB.
   - Verify row counts + checksum sampling.
   - Delete plaintext DB only after successful verification.
3) Provide rollback by keeping a backup of plaintext DB for one release cycle.

## Minimum viable implementation
- Phase 1: Secure session/token storage (done) and encrypted media cache.
- Phase 2: Android SQLCipher integration and migration path.
- Phase 3: iOS + Desktop encrypted DB rollout with telemetry and rollback support.

## Open items
- Decide SQLCipher licensing + dependency footprint.
- Define acceptable performance overhead and fallback behavior.
- Determine whether media blobs are stored outside DB (preferred).
