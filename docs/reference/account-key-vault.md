# Encrypted account-key recovery

LogDate keeps end-to-end encryption. The server-managed raw-key prototype, its routes, enabling flag,
dependency injection, and unpublished migration are removed. The request to approve server-managed
journal decryption is withdrawn.

## Intended sign-in flow

Sign in, unlock the journal locally, then sync. A compatible passkey derives a local encryption secret;
its output must never be included in the authentication request or uploaded to LogDate. The server
stores only encrypted key envelopes tied to the account and registered credential. Where automatic
unlock is unavailable, one existing-device approval belongs to sign-in. Recovery phrases remain a
lost-access mechanism. Google account authentication alone is not a journal decryption secret.

## Client envelope

Swift and Kotlin implement LDKE1: a five-byte version marker, 12-byte nonce, and 64 bytes of journal
identity/media material sealed with AES-256-GCM and a 16-byte authentication tag. HKDF-SHA256 derives
the wrapping key from a local 32-byte unlock secret, using the account/credential binding as salt and
`logdate-account-key-envelope-v1` as context. The header and binding are authenticated. Wrong accounts,
credentials, secrets, modified ciphertext, and plaintext key payloads fail closed.

The Mac requests native passkey PRF output and excludes that output from its encoded sign-in assertion.
Its account-key API accepts ciphertext only and no longer calls the raw-key endpoint. Android requests
PRF output locally and strips client extension results before sending its authentication response.
Both clients can provision a protected envelope from keys they already hold. A newly adopted account
cannot publish unrelated local identity keys. Android sync no longer publishes raw keys. Existing content, journal, association, and media formats are unchanged.

## Current evidence and remaining work

Client tests verify local wrapping and binding failures. A disposable loopback harness verifies both
Swift-to-Kotlin and Kotlin-to-Swift envelope decryption alongside journal edits, media cache, offline
relaunch, reconnect, and conflict choices. It substitutes a private fixture for the credential provider
and does not prove real passkey support or production sign-in.

Implemented locally: account/active-credential-bound storage at
`/api/v1/account/key-envelopes/{credentialId}`, canonical ciphertext validation, immutable writes that
accept byte-identical retries, account isolation, credential revocation checks, and deletion cascades.
The published V32 migration is unchanged. V33 removes the retired server-custody vault and
creates client-encrypted envelope storage, using no server wrapping key. The feature remains disabled
by default through `LOGDATE_ENCRYPTED_ACCOUNT_KEYS_ENABLED`; it advertises `encryptedAccountKeysV1`
only when enabled. Neither the raw-key route nor its old capability is advertised.

The Mac retains its account-bound PRF retry context in memory for five minutes and clears it on success,
new authentication, or reapproval. Missing or unsupported encrypted unlock continues into device
approval within sign-in. The QR includes the recipient key, account, device name, request and code;
Android verifies those local scan fields against the relay before encrypting anything. Old ID-only
codes are rejected. Updated Mac and Android clients are required together. After approval the Mac
protects its received keys for the passkey used to sign in, when compatible PRF output is available.

Still required: real-provider and real-account acceptance, review and release of the updated clients
and backend, and applying V33 to the deployed database. Existing accounts must create a protected envelope from an already unlocked device; the
server cannot recover missing identity keys from encrypted journal records. Credential-provider PRF
compatibility must be observed before claiming seamless cross-platform unlock.
