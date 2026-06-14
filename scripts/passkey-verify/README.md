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
.venv/bin/python sim.py --base https://cloud.logdate.app
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

The verifier saves its generated test passkey under `.logdate/passkey-verify/`
by default. That directory is gitignored. Reuse the saved passkey for a later
sign-in check with:

```bash
.venv/bin/python sim.py \
  --base https://cloud-staging.logdate.app \
  --username verify_… \
  --signin-only
```

For a custom location:

```bash
.venv/bin/python sim.py \
  --base https://cloud-staging.logdate.app \
  --username verify_… \
  --credential-file /secure/scratch/logdate-staging-passkey.json
```

## Flags

- `--base` HTTPS base URL (default `https://cloud.logdate.app`)
- `--origin` clientDataJSON origin (defaults to `--base`)
- `--username` defaults to `verify_<unix_ts>` (must be `[a-zA-Z0-9_]+`, ≥3 chars)
- `--display-name` defaults to `Deploy Verifier`
- `--credential-file` saves/loads the generated verifier passkey at a custom path
- `--signin-only` skips signup and verifies sign-in using the saved passkey
