# User-held recovery keys

Part of the Identity safeguards project in the LogDate on the AT Protocol initiative. The umbrella
design is [LogDate on the AT Protocol](./2026-10-09-logdate-atproto-interoperability-design.md),
which lists "people can hold their own recovery key on every platform" as the third safeguard
before any identity is published.

## The problem

Every LogDate account has an AT Protocol identity: a `did:plc` identifier and the
`alice.logdate.app` name that other people and apps know the account by. A `did:plc` identity is a
chain of signed operations. Each operation lists **rotation keys**, and only those keys can sign
the next operation. Today the only rotation key is the server's own signing key. So the server can
change anyone's identity by itself, and nobody can recover or move an identity without it.

A recovery key fixes that. It is a second rotation key that only the person holds. PLC ranks
rotation keys by their position in the list: a key earlier in the list can undo an operation signed
by a later key for 72 hours after it was made. Putting the person's key **first**, ahead of the
server's key, means the person can overrule the server.

## Decisions

### Key type: P-256, derived from the recovery phrase

The recovery key is a P-256 (`secp256r1`) key. PLC accepts P-256 and K-256 rotation keys, and P-256
is the curve every platform supports natively: CryptoKit on iOS, the JDK and BouncyCastle on Android
and desktop.

The key is derived from the person's existing 12-word recovery phrase, the same phrase that unlocks
their encrypted journal:

1. `CryptoManager.deriveMasterKey(phrase)` turns the phrase into a 32-byte master key.
2. `HMAC-SHA256(masterKey, "logdate.atproto.plc.recovery.v1:<attempt>")` gives a candidate private
   key, starting at attempt 0.
3. The first candidate that is a valid P-256 scalar (between 1 and the curve order) is the key.
   In practice attempt 0 always is; the loop exists so the derivation can never fail on an
   unlucky value.

The public half is published as a `did:key` (`did:key:zDn...`). Signatures are raw 64-byte `r || s`
values with a low `s`, base64url without padding, which is the form PLC requires.

Why the phrase rather than a new secret:

- **Nothing new to write down.** People already save and confirm their phrase. A second secret
  would be a second thing to lose, and most people would lose one of them.
- **Same recovery story everywhere.** Anyone who can restore the journal on a new device can also
  recover the identity there.
- **Deterministic.** Every device that holds the phrase derives the same key, so there is no key
  file to sync or back up.

The trade-off is that the phrase now protects the identity as well as the journal. The setup screen
says so plainly: anyone with the 12 words can take control of the identity.

### Where the key lives

Nowhere, at rest. The private key is derived in memory from the stored phrase each time it is
needed (to show its public half or to sign) and then dropped. Only the `did:key` and signatures
cross the network. The server never sees the phrase or the private key.

The phrase itself stays where it already is: in the device's secure storage and, where the platform
offers it, the device's own backup (Block Store on Android, iCloud Keychain on iOS).

### What the person writes down

Their 12-word recovery phrase, if they have not already. Setup reuses the existing Recovery phrase
screen: it shows the words behind the screen lock and then asks the person to type them back. The
recovery key can only be turned on once the phrase is saved on this device **and** confirmed.

### Registering the key

`POST /api/v1/identity/plc/recovery-key` with `{ "recoveryDidKey": "did:key:zDn..." }`.

The server builds a PLC update operation on top of the identity's latest operation:

- `rotationKeys` = `[recoveryKey, serverKey]`, with the person's key first;
- the signing key, handle and service endpoint stay the same;
- the server signs with its current key, which is allowed because that key is a rotation key of the
  previous operation.

Every operation the server builds afterwards (signing-key rotation, recovery) keeps the recovery key
first. Genesis operations created with a recovery key put it first too.

Registering the same key again does nothing. Registering a **different** key when one is already
set is refused with `409 RECOVERY_KEY_ALREADY_SET`. Otherwise someone holding only a stolen session
could quietly replace the person's key with their own.

### Recovering: replacing the server's key

The in-app recovery flow replaces the key the server uses for the identity. It is approved by the
recovery key, not the server:

1. `POST /api/v1/identity/recovery-key/recover/prepare`. The server generates a fresh signing key
   and builds an unsigned update operation:
   - `rotationKeys` = `[recoveryKey, newServerKey]`;
   - the `atproto` verification method is the new key;
   - `prev` is the latest operation.

   It returns the unsigned operation, the bytes to sign, and an opaque `preparedKey` token: the new
   private key sealed with the server's key-encryption key and bound to the account. The server
   stores nothing between the two calls.
2. The app derives the recovery key from the phrase on this device. It checks that the key matches
   the one registered on the identity, then signs the bytes.
3. `POST /api/v1/identity/recovery-key/recover/complete` with the token and signature. The server:
   - opens the token and checks that it belongs to this account;
   - rebuilds the operation and checks that `prev` is still the latest operation (otherwise
     `409 IDENTITY_CHANGED`);
   - checks that the registered recovery key is a rotation key of the latest operation;
   - verifies the signature against the registered recovery key;
   - appends the operation and makes the new key active.

Afterwards the old server key has no authority over the identity at all.

The screen says what will happen before anything is sent:

- this device approves the change with the recovery phrase, which never leaves the device;
- the key the server uses for the identity stops working and a new one replaces it;
- the recovery key still ranks above the new key;
- the journal, sign-in methods and devices do not change.

### Without publishing to the PLC directory

Publishing identities to the public PLC directory is off in production and stays off. Recovery keys
work without it:

- The server's **hosted operation log** (`hosted_plc_operations`) is the identity's history.
  When publishing is off, the latest operation is the newest entry in that log. When publishing is
  on, it is the latest entry in the directory's audit log, as before.
- Each stored operation now records its CID, computed locally from its DAG-CBOR encoding
  (`PlcEncoding.operationCid`). It is the same CID the directory would assign, so the log can be
  published unchanged later. Older rows without a CID have it computed from their stored JSON.
- Nothing is sent to the directory unless `ATPROTO_PLC_PUBLISH_ENABLED=true`.

The protection becomes complete once identities are published. The same key can then sign an
operation directly with the PLC directory, even when the server does not cooperate. Until then, the
in-app flow goes through the server, and the hosted log records every change.

### Gating

**Server.** A new environment flag, `ATPROTO_RECOVERY_KEYS_ENABLED`, is off by default. The server
supports recovery keys when it hosts `did:plc` identities and either this flag or PLC publishing is
on. When it does, it advertises the protocol feature `atprotoRecoveryKeysV1` in `/api/v1/info`.
When it does not, the recovery-key endpoints answer `409` with a specific code, and old
publishing-only behavior is unchanged.

**Client.** The `IDENTITY_RECOVERY_KEY` feature flag is off by default and can be turned on from
Labs. The Account screen shows the **Recovery key** row only when all of these are true:

- the flag is on;
- the person is signed in;
- the connected server advertises `atprotoRecoveryKeysV1`.

A server without the feature never shows the row. The app never offers a recovery action the server
cannot perform.

### Platforms

| Platform | Derivation and signing | Reachable today |
| --- | --- | --- |
| Android | BouncyCastle (`JvmPlcRecoveryKeySupport`) | Yes, when signed in |
| iOS | `cryptography-kotlin` over CryptoKit (`CryptographyPlcRecoveryKeySupport`) | Yes, when signed in |
| Desktop | BouncyCastle (`JvmPlcRecoveryKeySupport`) | Built and wired; the desktop app has no account sign-in yet, so the Account screen shows its signed-out state and the row is not reachable |

The previous iOS implementation threw on every call. It is replaced, and a shared test pins the
same `did:key` and checks signatures on Android, desktop and iOS.

### Copy

The copy never says DID, PLC, rotation key, did:key or operation. It talks about:

- "your identity" and the person's `@handle`;
- "the server that hosts your account", by its name;
- "your recovery phrase" and "your recovery key".

Errors say what failed and what to do: the server could not be reached, the phrase on this device
does not match, the identity changed while the request was in flight, or a different key is already
set.

## Testing

- **Shared crypto:** a fixed P-256 vector (the RFC 6979 A.2.5 key) gives the same `did:key` on every
  platform. Signatures are 64 bytes, low-`s` and verify. Derivation from a phrase is stable.
- **PLC encoding:** `operationCid` matches the CID the public directory assigned to a real genesis
  operation.
- **Server:** registration without publishing appends a correctly chained, correctly ordered
  operation. Re-registering is a no-op, and a different key is refused. Recovery round-trips with a
  real P-256 signature. A stale token, a wrong signature, a token from another account and an
  unregistered key are each refused with their own code. The descriptor advertises the feature only
  when supported.
- **Client:** the repository derives, matches and signs, and maps server codes. The view model
  covers every state. Screenshot tests render each screen state.
