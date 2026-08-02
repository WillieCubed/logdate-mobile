# Passkey end-to-end verifier

Drives a real WebAuthn flow against a live LogDate server: random EC P-256
keypair, valid CBOR attestation, real ECDSA signatures over
`authenticatorData ‖ SHA256(clientDataJSON)`, all base64url-encoded the way
a browser/platform authenticator would. The server's strict `webauthn4j`
verification has to accept the signed payloads — stub bytes (like the in-repo
`SyntheticPasskeyFixture`) only work when `WEBAUTHN_STRICT_VERIFICATION=false`,
which is dev/test only.

## Setup

```bash
uv venv .venv
uv pip install --python .venv/bin/python -r requirements.txt
```

## Run

```bash
.venv/bin/python sim.py \
  --base https://cloud.logdate.app \
  --expected-rp-id logdate.app
```

A successful run signs up a fresh user, then signs them back in:

```
=== SIGNUP verify_… ===
begin -> 200
complete -> 201
  body keys: ['account', 'tokens']
  saved passkey -> .logdate/passkey-verify/cloud.logdate.app/verify_….json

=== SIGNIN verify_… ===
begin -> 200
complete -> 200

=== END-TO-END PASSKEY VERIFICATION SUCCEEDED ===
```

Each run creates a real Postgres row in the deployed environment, so don't run
this against shared production accounts you care about — pick a unique
`--username` if you want a deterministic identity.

Deployment rollouts do not use this standalone form. They run
`scripts/smoke-test-revision.sh`, which creates a unique disposable account,
persists its passkey and opaque tokens in permission-`0600` files, and deletes
the account after the promoted revision proves the same media bytes are durable.

The verifier saves its generated test passkey under `.logdate/passkey-verify/`
by default. That directory is gitignored. Reuse the saved passkey for a later
sign-in check with:

```bash
.venv/bin/python sim.py \
  --base https://cloud-staging.logdate.app \
  --expected-rp-id logdate.app \
  --username verify_… \
  --signin-only
```

For a custom location:

```bash
.venv/bin/python sim.py \
  --base https://cloud-staging.logdate.app \
  --expected-rp-id logdate.app \
  --username verify_… \
  --credential-file /secure/scratch/logdate-staging-passkey.json
```

## Flags

- `--base` HTTPS base URL (default `https://cloud.logdate.app`)
- `--origin` clientDataJSON origin (defaults to `--base`)
- `--expected-rp-id` immutable RP ID from the deployment contract. The
  canonical origin host must equal this ID or be its subdomain, and both signup
  and signin options must return this exact value.
- `--username` defaults to `verify_<unix_ts>` (must be `[a-zA-Z0-9_]+`, ≥3 chars)
- `--display-name` defaults to `Deploy Verifier`
- `--credential-file` saves/loads the generated verifier passkey at a custom path
- `--signin-only` skips signup and verifies sign-in using the saved passkey
- `--state-file` atomically writes the disposable account ID, access token,
  refresh token, and credential-file path for the deployment smoke test. The
  file must already exist with mode `0600`; token and credential values are
  never written to stdout. State and credential replacements use a temporary
  file in the destination directory, fsync the file and directory, then replace
  the destination; deployment-media state uses the same writer rather than a
  cross-filesystem move.
- `--private-service-token-file` reads a Cloud Run ID token from a mode-`0600`
  file and sends it as `X-Serverless-Authorization` on every verifier request.
  The token value is never passed on the command line.

## Deployment durability phases

The immutable-contract interface is intentionally explicit. The renderer must
include `android_package_name` before Task 6 switches official callers; there is
no package-name default in the smoke proof. The rollout environment must export
`HEALTH_INTERNAL_TOKEN` from its environment-scoped secret store; it is never a
command-line flag.

Task 6's rollout writes an audience-bound Cloud Run identity token directly to
`$INVOKER_TOKEN_FILE` without exposing it in argv. Prepare the empty state file
and lock both inputs before invoking this script:

```bash
umask 077
: > "$SMOKE_STATE_FILE"
chmod 600 "$INVOKER_TOKEN_FILE" "$SMOKE_STATE_FILE"
test -s "$INVOKER_TOKEN_FILE"
```

Before promotion, run `prepare` against the private tagged candidate URL. It
proves the exact release, authenticated database health, first-party descriptor,
full Digital Asset Links certificate set, protected-route `401`, passkey signup
and signin, the seeded finite free quota, and a random media upload. The state
and credential files intentionally survive this successful phase.

```bash
scripts/smoke-test-revision.sh \
  --service-url "$CANDIDATE_URL" \
  --contract-file "$CONTRACT_FILE" \
  --expected-release "logdate-server@$RELEASE_SHA" \
  --invoker-token-file "$INVOKER_TOKEN_FILE" \
  --phase prepare \
  --state-file "$SMOKE_STATE_FILE"
```

After promotion, run `verify-and-cleanup` through the canonical public origin.
It signs in with the same passkey, requires `GET /auth/me` to return the exact
prepared account ID and username, downloads and hashes the candidate-created
media, and checks quota usage. Cleanup then deletes and proves absence of the
media, logs out and proves the stored refresh token is rejected, deletes and
proves absence of the account, and finally erases local state. The
invoker-token flag remains syntactically explicit but is not read in this
canonical phase.

The retained credential keeps the WebAuthn user handle separate from the
account ID. The handle is the server-issued base64url encoding of the canonical
UUID text and is sent back only as assertion `userHandle`; its decoded UUID must
equal the plain UUID in `data.account.id`. The credential is independently
bound to its canonical WebAuthn origin, exact username, credential ID, account
ID, and candidate base. That base can migrate only through the explicit
candidate-to-canonical handoff recorded in private state.

Successful auth must include both `data.account.id` and
`data.account.username`, plus access and refresh tokens. Swapped or missing
account fields are never authorized for deletion. If signup completed but its
HTTP-`201` body is malformed or cannot be fully bound, the verifier retains a
permission-`0600`, deploy-state-v2 recovery marker and credential instead of
using unbound tokens. A later `prepare` or `verify-and-cleanup` invocation can
sign in with that retained credential, prove the exact identity, and finish
cleanup. State-write and pre-reauthentication failures retain recovery material;
they never authorize deletion with provisional or stale tokens.

```bash
scripts/smoke-test-revision.sh \
  --service-url "$CANONICAL_ORIGIN" \
  --contract-file "$CONTRACT_FILE" \
  --expected-release "logdate-server@$RELEASE_SHA" \
  --invoker-token-file "$INVOKER_TOKEN_FILE" \
  --phase verify-and-cleanup \
  --state-file "$SMOKE_STATE_FILE"
```

`health-only` runs the release, database, descriptor, asset-links, and
unauthenticated-route gates without creating an account. Candidate and
`health-only` requests require a mode-`0600` invoker token file. Any failure
after exact reauthentication triggers resumable remote cleanup through the URL
for that phase. Cleanup persists separate identity, media-deletion,
refresh-revocation, and account-deletion phases. It stops before revocation or
account deletion when media cleanup fails, and stops before account deletion
when logout or refresh-rejection proof fails. State and credential stay on disk
until every phase succeeds, so the next invocation resumes safely. Failures
before exact `GET /auth/me` identity proof retain state without issuing any
destructive request. Revocation and account-deletion intent are checkpointed
before their remote side effects, allowing a retry to accept a proven
already-revoked or already-deleted `401` only after an earlier attempt.

All verifier HTTP requests disable redirects. This prevents private Cloud Run,
bearer, or other session headers from being forwarded across a `30x` hop.

HTTPS origins are parsed structurally, including bracketed IPv6 and valid
ports; userinfo, paths, queries, fragments, empty hosts, trailing-dot hosts, and
invalid ports fail closed. Each Android apk-key-hash is canonically decoded to
exactly 32 bytes and must equal the base64url encoding derived from its paired
colon-hex SHA-256 certificate fingerprint.

The positional `smoke-test-revision.sh <url> [origin]` form remains temporarily
available with a deprecation warning so the current deployment workflow is not
broken. Task 6 must switch every official caller to the immutable contract and
then remove that compatibility path atomically.
