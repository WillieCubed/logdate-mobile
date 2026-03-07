# User Journeys

Each journey is a complete narrative of a user interacting with the system. These map directly to [acceptance criteria](./03-acceptance-criteria.md).

---

## Journey 1: New User Creates an Account

**Actor**: Someone installing LogDate for the first time.

**Context**: The user has never used LogDate. They install the app and go through onboarding. They decide to create a LogDate Cloud account.

### Steps

1. User installs LogDate and completes local onboarding.
   - Device generates a local UUID (device ID) via `DeviceIdProvider`.
   - `IdentityKeyManager.setupNewIdentity()` generates a BIP-39 recovery phrase and derives the local identity key.
   - User is prompted to write down their recovery phrase.

2. User chooses to create a LogDate Cloud account.
   - User enters a username (e.g., `alice`) and display name.
   - Client calls `POST /api/v1/auth/signup/passkey/begin` with preferred username.
   - Server generates a temporary session, WebAuthn challenge.
   - Client presents passkey creation UI (Android Credential Manager).
   - User creates a passkey via biometrics/PIN.
   - Client calls `POST /api/v1/auth/signup/passkey/complete` with credential.

3. **Server creates the account with DID identity (NEW)**.
   - Server creates the account record with a UUID.
   - Server generates `did:web:logdate.app:users:alice`.
   - Server generates an Ed25519 signing keypair via `SigningKeyService`.
   - Server stores the signing key (private key encrypted at rest) in `SigningKeysTable`.
   - Server stores DID and public key in `AccountsTable`.
   - Server returns `AccountCreationResponse` including the DID.

4. Client receives the response.
   - `UserIdentity` is updated with `did = "did:web:logdate.app:users:alice"`.
   - `CloudAccount` model now includes the DID.
   - User sees their profile with username `alice` -- the DID is stored but not prominently displayed (it's infrastructure, not UI).

5. DID Document becomes available.
   - Anyone can now fetch `https://logdate.app/users/alice/did.json` and get a valid DID Document.
   - `https://logdate.app/.well-known/atproto-did?handle=alice.logdate.app` returns the DID.

### What changed from current flow
- Steps 1-2 are identical to today.
- Step 3 adds DID and signing key generation (invisible to the user).
- Step 4 adds DID to client models (stored, not displayed prominently).
- Step 5 is entirely new server behavior.

---

## Journey 2: Existing User Migrates to DID Identity

**Actor**: A user who created their account before DID support was added.

**Context**: The user has been using LogDate with a UUID-only account. After an app update, the server assigns them a DID.

### Steps

1. Server deploys the database migration.
   - `AccountsTable` gains nullable `did` and `signingKeyPublic` columns.
   - No data loss. No downtime. Existing accounts have `did = NULL`.

2. Background migration job runs.
   - For each account where `did IS NULL`:
     - Generate `did:web:logdate.app:users:{username}`.
     - Generate Ed25519 signing keypair.
     - Store in `SigningKeysTable` and update `AccountsTable`.
   - Job is idempotent and can be re-run safely.
   - Processes accounts in batches to avoid database pressure.

3. User opens the app after updating.
   - Client calls `GET /api/v1/auth/me` (existing endpoint).
   - Response now includes `did` field.
   - Client updates local `UserIdentity` and `CloudAccount` with the DID.
   - No user action required. No migration UI. Completely transparent.

4. User's DID Document is now live.
   - Same as Journey 1, Step 5.

### What the user sees
Nothing different. The DID is silently added to their account. Their existing passkeys, journals, and sync continue to work exactly as before.

---

## Journey 3: Third-Party AT Protocol Client Authenticates

**Actor**: A developer who built a web-based journal viewer that speaks AT Protocol. A LogDate user wants to sign in with their LogDate identity.

**Context**: The third-party client knows the user's handle (`alice.logdate.app`) and wants to access their data via standard AT Protocol OAuth.

### Steps

1. Third-party client resolves the user's handle.
   - Client fetches `https://logdate.app/.well-known/atproto-did?handle=alice.logdate.app`.
   - Gets back: `did:web:logdate.app:users:alice`.

2. Client resolves the DID to find the PDS.
   - Client fetches `https://logdate.app/users/alice/did.json`.
   - DID Document contains `service[#atproto_pds].serviceEndpoint = "https://logdate.app"`.

3. Client discovers the OAuth server.
   - Client fetches `https://logdate.app/.well-known/oauth-authorization-server`.
   - Gets OAuth metadata: authorization endpoint, token endpoint, PAR endpoint, supported flows.

4. Client initiates OAuth with PAR (Pushed Authorization Request).
   - Client sends a POST to `https://logdate.app/oauth/par` with:
     - `client_id`: URL pointing to the client's metadata document
     - `redirect_uri`: where to send the user after auth
     - `scope`: `atproto` (or `transition:generic`)
     - `code_challenge` + `code_challenge_method`: PKCE S256
     - `login_hint`: `alice.logdate.app` (the handle)
   - Server validates the client metadata (fetches the `client_id` URL).
   - Server returns a `request_uri`.

5. Client redirects user to LogDate's authorization endpoint.
   - Browser navigates to `https://logdate.app/oauth/authorize?request_uri=urn:ietf:params:oauth:request_uri:abc123`.
   - On Android, this could be handled via an app link, opening the LogDate app directly.

6. **LogDate authenticates the user with a passkey.**
   - LogDate's authorization UI presents a passkey challenge.
   - User authenticates with biometrics/PIN (same `WebAuthnPasskeyService`, unchanged).
   - Server verifies the passkey, confirms the user is `alice`.

7. Server issues an authorization code.
   - User is shown: "Journal Viewer wants to access your LogDate data. Allow?"
   - User approves.
   - Server generates an authorization code and redirects to the client's `redirect_uri`.

8. Client exchanges code for tokens.
   - Client sends POST to `https://logdate.app/oauth/token` with:
     - `grant_type`: `authorization_code`
     - `code`: the authorization code
     - `code_verifier`: PKCE proof
     - `DPoP` header: proof JWT signed by client's ephemeral key
   - Server validates code, PKCE, and DPoP proof.
   - Server returns a DPoP-bound access token with `sub = did:web:logdate.app:users:alice`.

9. Client uses the token to access data.
   - Client includes `Authorization: DPoP <token>` and `DPoP: <proof>` headers in API requests.
   - Server validates the token and DPoP binding, resolves the DID to an account, and serves data.

### What makes this possible
- Handle resolution (Phase 2) lets the client discover the user's PDS.
- OAuth server (Phase 3) lets the client authenticate without knowing LogDate's proprietary API.
- Passkeys (unchanged) provide the actual user authentication.
- DPoP binding prevents token theft.

---

## Journey 4: User Exports Identity to Move to Another PDS

**Actor**: A LogDate user who wants to move their data and identity to a self-hosted instance or a different AT Protocol PDS.

**Context**: Alice has been using `logdate.app` but wants to run her own server at `alice.example.com`. She needs to take her identity with her.

### Steps (did:web)

1. User requests signing key export.
   - In Settings > Account > Data Portability, user taps "Export Identity."
   - Client calls `GET /api/v1/identity/signing-key/export` (requires passkey auth).
   - Server returns the signing key encrypted with a key derived from the user's recovery phrase.
   - User saves the encrypted key export file.

2. User exports their data.
   - User uses the existing data export feature (ZIP with journals, notes, media).
   - Export metadata now includes the DID.

3. User sets up their new server.
   - User deploys a LogDate instance (or compatible AT Protocol PDS) at `alice.example.com`.
   - User imports the encrypted signing key (decrypted using their recovery phrase).
   - User imports their data.

4. User updates their identity.
   - **did:web scenario**: User's new DID becomes `did:web:alice.example.com`. This is a new DID -- the old one (`did:web:logdate.app:users:alice`) stops resolving when LogDate removes the account.
   - **did:plc scenario** (Journey 5): User updates their PLC entry to point to the new server. The DID stays the same.

5. User deletes their account on logdate.app.
   - LogDate removes the account, DID Document, and all data.
   - LogDate provides cryptographic proof of deletion.

### Limitations of did:web portability
With did:web, moving servers means getting a new DID. This is the fundamental trade-off of did:web (identity tied to domain). Journey 5 (did:plc upgrade) solves this for users who need true portability.

### Steps (did:plc) -- requires Phase 4

1-2. Same as above.

3. User sets up their new server.
   - Imports signing key and data.

4. User updates their PLC entry.
   - User signs a PLC operation (using their signing key or recovery key) that updates:
     - `service.atproto_pds.endpoint` from `https://logdate.app` to `https://alice.example.com`
     - `handle` from `alice.logdate.app` to `alice.example.com`
   - PLC directory accepts the operation (signed by authorized key).
   - DID stays the same: `did:plc:abc123`.

5. User deletes their account on logdate.app.
   - LogDate removes local data. The DID lives on in the PLC directory, now pointing to the new server.

---

## Journey 5: User Upgrades from did:web to did:plc

**Actor**: An existing LogDate user who wants their identity to survive domain changes.

**Context**: Alice has been using `did:web:logdate.app:users:alice` and wants a `did:plc` for stronger portability guarantees.

### Steps

1. User navigates to Settings > Account > Identity.
   - Sees current identity: `did:web:logdate.app:users:alice`.
   - Option: "Create portable identity (did:plc)."

2. User initiates did:plc creation.
   - Client calls `POST /api/v1/identity/upgrade-to-plc` (requires passkey auth).
   - Server constructs a PLC genesis operation:
     - `rotationKeys`: includes the user's recovery-phrase-derived key (so the user can rotate without the server)
     - `verificationMethods.atproto`: the existing Ed25519 signing key
     - `services.atproto_pds.endpoint`: `https://logdate.app`
     - `alsoKnownAs`: `at://alice.logdate.app`
   - Server signs the operation with the signing key.
   - Server submits the operation to `plc.directory`.
   - PLC directory returns the new DID: `did:plc:abc123`.

3. Server updates the account.
   - `AccountsTable.did` changes from `did:web:logdate.app:users:alice` to `did:plc:abc123`.
   - The old `did:web` path (`/users/alice/did.json`) now returns a DID Document for the `did:plc`, as a convenience redirect.

4. Client updates.
   - `UserIdentity.did` changes to the new did:plc.
   - All functionality continues unchanged.

5. User now has true portability.
   - If they ever leave LogDate, they can update their PLC entry to point to a new PDS.
   - Their DID never changes.

---

## Journey 6: User Rotates Their Signing Key

**Actor**: A LogDate user who suspects their signing key may be compromised, or who wants to rotate keys as a security practice.

### Steps

1. User navigates to Settings > Account > Security > Signing Keys.
   - Sees current key fingerprint and creation date.
   - Option: "Rotate signing key."

2. User initiates rotation.
   - Client calls `POST /api/v1/identity/signing-key/rotate` (requires passkey auth).
   - Server generates a new Ed25519 keypair.
   - Server stores the new key in `SigningKeysTable`.
   - Server sets `revokedAt` on the old key (it remains in the table for historical verification).
   - Server updates `AccountsTable.signingKeyPublic` to the new key.

3. DID Document updates.
   - The DID Document at `/users/{username}/did.json` now contains the new public key.
   - For did:plc users: server signs a PLC operation to update the verification method. This requires the existing signing key (before it's fully rotated) or the rotation key.

4. Historical content remains verifiable.
   - Old signatures were made with the old key.
   - The old key's public component is retained in `SigningKeysTable`.
   - Verification of historical content looks up the key that was active at the time of signing.

---

## Journey 7: User Recovers Identity on a New Device

**Actor**: A LogDate user who got a new phone and needs to restore their account.

### Steps

1. User installs LogDate on the new device.
   - Device generates a temporary local UUID and identity key.

2. User signs in with their existing passkey.
   - If the passkey synced across devices (e.g., via iCloud Keychain or Google Password Manager), the user can authenticate directly.
   - Client calls `POST /api/v1/auth/signin/passkey/begin`, then `/complete`.
   - Server returns account info including the DID.

3. Client restores identity.
   - `UserIdentity` is populated with the cloud account's UUID and DID.
   - Local device ID is mapped to the primary account ID (existing migration flow).
   - Sync begins pulling data from the server.

4. **Alternative: passkey didn't sync.** User uses recovery phrase.
   - User enters their 12-word recovery phrase.
   - `IdentityKeyManager.recoverIdentity(phrase)` restores the local encryption key.
   - User creates a new passkey on this device (re-registers with the server).
   - Account is now accessible from the new device with a new passkey.

5. Signing key is unaffected.
   - The signing key lives on the server, not the device.
   - No signing key migration is needed for device changes.
   - This is a key advantage of the custodian model: device changes don't affect AT Protocol identity.

---

## Journey 8: External AT Protocol User Discovers a LogDate User

**Actor**: A Bluesky user or AT Protocol developer who wants to look up a LogDate user.

### Steps

1. External user knows the handle `alice.logdate.app`.

2. They resolve the handle.
   - Their client fetches `https://logdate.app/.well-known/atproto-did?handle=alice.logdate.app`.
   - Response: `did:web:logdate.app:users:alice` (plain text).
   - Alternatively, their client calls `GET /xrpc/com.atproto.identity.resolveHandle?handle=alice.logdate.app`.
   - Response: `{"did": "did:web:logdate.app:users:alice"}`.

3. They resolve the DID.
   - Client fetches `https://logdate.app/users/alice/did.json`.
   - Gets the DID Document with verification method and PDS service endpoint.

4. They discover the PDS.
   - Service endpoint is `https://logdate.app`.
   - Client can call `GET /xrpc/com.atproto.server.describeServer` to learn about the server's capabilities.

5. What they can do.
   - Verify that content signed by `did:web:logdate.app:users:alice` is authentic (using the public key from the DID Document).
   - Discover that LogDate is the user's PDS.
   - (Future, with Lexicon schemas): Read the user's public journal entries via AT Protocol repo endpoints.

6. What they cannot do (yet).
   - Follow/interact via Bluesky (requires shared Lexicon schemas, which is future work).
   - Access the user's data via AT Protocol repo sync (requires MST implementation, which is future work).

---

## Journey 9: User Deletes Their Account

**Actor**: A LogDate user who wants to completely remove their presence.

### Steps

1. User navigates to Settings > Account > Delete Account.
   - Shown: "This will permanently delete your account, all your data, and your identity from LogDate. If you have a did:plc, it will continue to exist in the PLC directory but will no longer point to LogDate."

2. User confirms with passkey authentication.
   - Extra-strong confirmation: passkey auth required (not just a button tap).

3. Server processes deletion.
   - All user data is deleted (journals, notes, media, sync records).
   - Signing keys are deleted from `SigningKeysTable`.
   - Account record is deleted from `AccountsTable`.
   - DID Document at `/users/{username}/did.json` returns 404.
   - Handle resolution for this handle returns 404.

4. For did:plc users.
   - The PLC entry still exists (LogDate cannot delete it -- this is by design in did:plc).
   - The PLC entry still points to `https://logdate.app` as the PDS endpoint.
   - LogDate should (optionally) sign a PLC "tombstone" operation that marks the DID as deactivated.
   - If the user wants to reactivate on a different PDS, they can use their recovery key to sign a new PLC operation pointing elsewhere.

5. For did:web users.
   - The DID effectively ceases to exist (the document is no longer served).
   - This is a known limitation of did:web.

6. Server provides deletion confirmation.
   - Returns a signed attestation of deletion (timestamp, account ID, what was deleted).
   - User can keep this as proof that LogDate fulfilled its custodian obligation.
