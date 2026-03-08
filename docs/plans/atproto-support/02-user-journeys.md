# User Journeys

These journeys describe the current target architecture and the shipped AT Protocol slices in this repo.

## Defaults

- Hosted multi-user LogDate accounts default to `did:plc`.
- Hostname-level `did:web` remains supported for dedicated or custom-domain deployments.
- The first-party LogDate app keeps its existing bearer-JWT auth path.
- Third-party AT Protocol clients use OAuth 2.0 with DPoP.
- The current repo-backed XRPC write path is a compatibility adapter over LogDate content records, not the final MST/CAR repo implementation.

## Journey 1: Hosted User Signs Up and Receives a Portable Identity

**Actor**: A new LogDate user creating an account in the first-party app.

1. The user completes the passkey signup flow through the existing `api/v1/auth/signup/passkey/*` endpoints.
2. The server creates the account record exactly as before.
3. AT Protocol identity provisioning runs for the new account.
4. LogDate assigns a managed handle such as `alice.logdate.app`.
5. LogDate ensures an active signing key exists for the account.
6. When hosted PLC identities are enabled, LogDate provisions a hosted `did:plc`.
7. The account record stores:
   - `did`
   - `handle`
   - `signingKeyPublic`
8. The signup response includes the DID and handle.
9. The app stores the DID and handle in `CloudAccount` and `UserIdentity`.

**Outcome**

- The user can keep using LogDate exactly as before.
- Their account now has a standards-based public identity and signing key material.

## Journey 2: Existing User Is Backfilled Into AT Protocol Identity

**Actor**: An existing LogDate user created before AT Protocol identity support.

1. The server starts and runs `backfillMissingIdentities()`.
2. For each account missing identity data, LogDate:
   - normalizes any existing handle or DID
   - provisions a unique managed handle if needed
   - provisions a hosted DID using the configured hosted DID method
   - ensures an active signing key exists
3. The account is saved back with normalized identity fields.

**Outcome**

- Existing accounts gain DID, handle, and signing key metadata without changing passkey auth or sync behavior.

## Journey 3: Third-Party Client Resolves a User and Inspects Their Repo

**Actor**: An external AT Protocol-compatible client that only knows a handle.

1. The client resolves the handle through standard AT Protocol handle resolution.
2. For hosted LogDate users, the handle resolves to a `did:plc`.
3. The client resolves the DID:
   - `did:plc` through the PLC directory
   - `did:web` through `/.well-known/did.json` on the hostname
4. The DID Document exposes:
   - `alsoKnownAs = at://<handle>`
   - the active public signing key
   - `#atproto_pds` pointing at the LogDate PDS base URL
5. The client calls:
   - `com.atproto.server.describeServer`
   - `com.atproto.repo.describeRepo`
   - `com.atproto.repo.getRecord` or `listRecords`

**Outcome**

- The client can discover the user’s PDS and inspect the currently exposed collection set without any LogDate-specific API contract.

## Journey 4: Third-Party Client Completes OAuth + DPoP

**Actor**: An external client that wants scoped write access.

1. The client fetches:
   - `/.well-known/oauth-authorization-server`
   - `/.well-known/oauth-protected-resource`
   - `/oauth/jwks`
2. The client creates a DPoP key pair.
3. The client sends `POST /oauth/par` with:
   - `client_id`
   - `redirect_uri`
   - `scope`
   - PKCE parameters
   - a DPoP proof
4. LogDate fetches and validates the client metadata document at `client_id`.
5. LogDate stores the pushed request in memory and returns a `request_uri` plus the current DPoP nonce.
6. The user authenticates to LogDate through the existing first-party session path.
7. The user visits `GET /oauth/authorize?request_uri=...` while authenticated.
8. LogDate resolves the authenticated account to its DID and handle and shows the consent prompt payload.
9. On approval, LogDate redirects back with a short-lived authorization code.
10. The client exchanges the code at `POST /oauth/token` with:
    - `grant_type=authorization_code`
    - `code_verifier`
    - `client_id`
    - a DPoP proof bound to the same DPoP key
11. LogDate returns:
    - a DPoP-bound access token
    - a refresh token
    - `sub = <user DID>`
12. The client calls XRPC write endpoints using:
    - `Authorization: DPoP <access-token>`
    - `DPoP: <proof>`

**Outcome**

- Third-party clients can authenticate through standards-based OAuth without knowing the LogDate JWT format.

## Journey 5: First-Party App Keeps Using the Existing Auth Path

**Actor**: The LogDate mobile app.

1. The app signs in through the existing passkey-based auth endpoints.
2. The server still issues the current LogDate bearer JWTs.
3. Account payloads now also include DID and handle metadata.
4. The app continues to call existing LogDate APIs.
5. When needed, the app can also call identity export or XRPC endpoints using the existing bearer token.

**Outcome**

- No first-party UX regression is required to adopt AT Protocol identity.
- OAuth exists for interoperability, not as a forced replacement for the current app session model.

## Journey 6: User Exports Their Signing Key

**Actor**: A user who wants a recoverable copy of their AT Protocol signing key.

1. The app calls `POST /api/v1/identity/signing-key/export` with the current bearer token and a passphrase.
2. LogDate loads the active signing key for the account.
3. The private key is decrypted from server storage.
4. LogDate re-encrypts it with a passphrase-derived AES key using PBKDF2 + AES-GCM.
5. The response includes:
   - `did`
   - `handle`
   - `publicKeyMultibase`
   - `publicKeyDidKey`
   - encrypted private-key payload and KDF metadata

**Outcome**

- The user has a portable encrypted export of the current account signing key.

## Journey 7: Dedicated Deployment Uses Hostname-Level did:web

**Actor**: A self-hosted or dedicated LogDate deployment.

1. The deployment is configured to use hosted DID method `WEB`.
2. A user account is provisioned with a hostname-level `did:web`.
3. The deployment serves `/.well-known/did.json` for that hostname.
4. AT Protocol clients resolve the DID directly through the hostname.

**Outcome**

- LogDate still supports standards-based `did:web` where it fits the deployment model.
- Path-based `did:web` is never used.

## Journey 8: Repo-Style XRPC Writes Map to Existing LogDate Content

**Actor**: A client writing the current compatibility collection.

1. The client authenticates with either:
   - LogDate bearer JWT
   - OAuth DPoP access token
2. The client calls:
   - `com.atproto.repo.createRecord`
   - `com.atproto.repo.putRecord`
   - `com.atproto.repo.deleteRecord`
3. The server routes the write through `AtprotoContentRecordStore`.
4. That store maps the XRPC record to LogDate’s existing content sync records.
5. Reads from `getRecord` and `listRecords` surface the same compatibility collection.

**Outcome**

- LogDate exposes a small standalone PDS-like surface now while the full repo implementation remains future work.
