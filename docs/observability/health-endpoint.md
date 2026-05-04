# `/health` endpoint and the internal token

`GET /health` is the single liveness/readiness URL the LogDate server exposes.
The same route serves two audiences with one URL — the open internet sees a
deliberately minimal payload, and an internal monitor presenting a shared
secret in `X-LogDate-Health-Token` gets a richer payload that includes
deployment-internal fields. The header is purely additive — wrong token,
missing header, or unset env var on the server all return the public payload
with HTTP 200, so the external surface gives away nothing about the
deployment's internal state.

## Payloads

**Public** (no header, wrong header, or `HEALTH_INTERNAL_TOKEN` unset on the
server):

```json
{
    "status": "healthy",
    "timestamp": "2026-05-04T22:43:57.841778716Z",
    "version": "1.0.0"
}
```

**Internal** (correct `X-LogDate-Health-Token` matching env
`HEALTH_INTERNAL_TOKEN` on the server):

```json
{
    "status": "healthy",
    "timestamp": "2026-05-04T22:43:57.841778716Z",
    "version": "1.0.0",
    "db_connected": true
}
```

`db_connected` is the field a status monitor needs to distinguish "JVM bound
port 8080" from "actually serving requests against Postgres". Without it, a
`status: 200` check stays green when the database is unreachable but the
server is still answering its own liveness probe.

## Why the header instead of a separate `/health/internal` route

Two surfaces means two URLs to keep aligned, two sets of monitor configs to
keep in sync if either changes, and a discoverable second route an attacker
can probe to learn that internal endpoints exist. One route + an opt-in
header is the strictly smaller surface — public callers see exactly one
shape, internal callers see exactly one URL.

## Design constraints

1. **Public callers must not see internal fields.** The token gate is
   strictly additive — a missing or wrong token never elevates the response.
   Tested in
   `server/src/test/kotlin/app/logdate/server/ApplicationTest.kt`:
   `testHealthOmitsInternalDetailsWithoutToken`,
   `testHealthOmitsInternalDetailsWithWrongToken`,
   `testHealthOmitsInternalDetailsWhenTokenUnconfigured`.

2. **Wrong tokens get the public payload, not 401.** A 401 would let a
   probe distinguish "this route accepts a token" from "this route is just a
   normal endpoint." Treating wrong tokens as no-token keeps both
   indistinguishable from the outside. The trade-off is that an internal
   monitor with a stale token silently downgrades to the public payload —
   that's caught by the monitor's `db_connected:true` assertion, which fails
   the moment the field disappears from the response and pages on it.

3. **Token comparison is constant-time.** Implemented with
   `java.security.MessageDigest.isEqual`, not `==`. A simple string
   comparison short-circuits on the first byte mismatch, so the response
   timing leaks how many leading bytes of the attacker's guess matched the
   server's secret. With a constant-time comparison every guess takes the
   same time, regardless of how close the guess is. Necessary because the
   route is unauthenticated and unrate-limited at the public edge.

4. **Token is per-environment.** The staging token and production token
   must be different secrets. If they were the same, a leak from staging
   would compromise prod's internal-health surface and vice versa.
   Staging's secret lives in the `logdate-dev` GCP project; prod's lives in
   `logdate`. Same secret name (`logdate-health-internal-token`), distinct
   contents.

5. **Token is purely opt-in for the server.** If `HEALTH_INTERNAL_TOKEN`
   isn't set in the Cloud Run environment, the server still boots, `/health`
   still works, and no caller can ever unlock the internal payload. There's
   no boot-time fail-fast on this — losing the token means losing depth of
   monitoring, not losing the server.

6. **Token is short-lived in the working tree.** The actual secret never
   appears in any source-controlled file — it lives only in GCP Secret
   Manager and the operator's password manager. Rotation is a Secret Manager
   version bump + a Cloud Run revision update; no code change.

## Generating a fresh token

Any cryptographically random opaque string is fine. The server treats it as
opaque bytes — there's no encoding requirement. 32+ bytes of randomness is
plenty for an HMAC-equivalent shared secret.

```bash
openssl rand -hex 32
```

(64 hex characters = 256 bits of entropy. Anything from
`openssl rand -base64 24` upward works equally well.)

## Provisioning into a GCP environment

For each environment (staging = `logdate-dev`, production = `logdate`):

```bash
PROJECT=logdate-dev          # or logdate
SERVICE=logdate-server-staging   # or logdate-server
REGION=us-central1

# 1. Create the Secret Manager secret.
openssl rand -hex 32 \
  | gcloud secrets create logdate-health-internal-token \
      --project="$PROJECT" \
      --replication-policy=automatic \
      --data-file=-

# 2. Grant the Cloud Run runtime SA read access.
RUNTIME_SA=$(gcloud run services describe "$SERVICE" \
  --project="$PROJECT" --region="$REGION" \
  --format='value(spec.template.spec.serviceAccountName)')

gcloud secrets add-iam-policy-binding logdate-health-internal-token \
  --project="$PROJECT" \
  --member="serviceAccount:$RUNTIME_SA" \
  --role=roles/secretmanager.secretAccessor

# 3. Mount the secret as the HEALTH_INTERNAL_TOKEN env var.
gcloud run services update "$SERVICE" \
  --project="$PROJECT" --region="$REGION" \
  --update-secrets=HEALTH_INTERNAL_TOKEN=logdate-health-internal-token:latest
```

The Cloud Run rollout is a fresh revision; existing in-flight requests
finish on the old revision. After ~30s the new revision is serving 100% of
traffic and `curl -H "X-LogDate-Health-Token: <token>" https://.../health`
returns the enriched payload.

## Rotating the token

```bash
# 1. Add a new secret version.
openssl rand -hex 32 \
  | gcloud secrets versions add logdate-health-internal-token \
      --project="$PROJECT" --data-file=-

# 2. Force a new revision so it picks up the latest.
gcloud run services update "$SERVICE" \
  --project="$PROJECT" --region="$REGION" \
  --update-secrets=HEALTH_INTERNAL_TOKEN=logdate-health-internal-token:latest
```

Update the monitor's header value at the same time. The window between the
revision rolling out and the monitor being updated is the window in which
`db_connected:true` is missing — the monitor's assertion will catch it and
page.

Old versions can be left in place (Secret Manager keeps them by default) or
explicitly destroyed once you've confirmed the new version works.

## Why a fixed shared secret instead of a generator (HMAC, JWT, OIDC, mTLS, …)?

A few stronger schemes were considered:

| Scheme | What changes | Strength gained | Cost |
|---|---|---|---|
| HMAC-signed timestamp (`ts.hmac(key, ts)`) | Server validates HMAC + ts within ±5 min window | Replay-resistant; leaked token expires in minutes | Monitor must compute HMAC per probe (most uptime services don't support per-request scripting); clock-sync requirement |
| Short-lived JWT (`Authorization: Bearer …`) | Server validates signature + `exp` against shared key | Same as HMAC; reuses our existing JWT infra | Same monitor-side scripting requirement; overkill for a liveness probe |
| OIDC ID token (Google service account, validated against Google JWKS) | Server validates Google-issued ID token | Tokens auto-rotate every ~1 h; never holds a long-lived secret | Monitor needs Google SA credentials and must mint a fresh ID token per probe; Better Stack / Pingdom / UptimeRobot don't support this |
| mTLS (client cert) | Server validates client cert against a CA | No bearer secret crosses the wire | Cloud Run doesn't surface client-cert info to the app cleanly; very few external monitors do mTLS |
| TOTP (RFC 6238) | Both sides compute the current 30s window code from a shared seed | Replay-resistant within a 30 s window | Same custom-scripting requirement |
| GCP IAP in front of the route | Google Cloud Identity-Aware Proxy gates the URL | No app-side auth code; rotate via IAM grants | Monitor must authenticate as a Google identity (most don't); IAP outage = monitor outage |

**The deciding constraint is the monitor side, not the server side.** Most
managed uptime services (Better Stack, Pingdom, UptimeRobot, StatusCake) only
let you set static request headers. Anything that requires computing a token
per probe means either self-hosting the monitor or moving to a more expensive
scripting-capable service (Datadog Synthetics, Checkly, k6 Cloud), which
costs more in runtime and money than the marginal security improvement is
worth — the worst-case leak of this token is "the database is up," which
an attacker could already infer from any other API call working.

**The fixed-shared-secret design doesn't preclude a future move to a
generator.** If we later switch to a richer monitor that supports custom
auth, the env var becomes a key instead of a literal token, the comparison
becomes "validate this signature with this key," and the monitor side starts
computing per-request — same code shape, swap implementations.

**The middle-ground that's worth doing now**: automate rotation of the
fixed token (a scheduled Cloud Function or GH Actions cron that bumps the
Secret Manager version every 90 days and updates the monitor's header via
the monitor's API). That gets most of the benefit of generator tokens —
a leaked token expires automatically — without breaking compatibility with
simple monitors. Not implemented today; a worthwhile follow-up if the
operational burden of manual rotation starts to bite.

## Configuring the external monitor

For Better Stack (or equivalent):

- **URL**: `https://cloud.logdate.app/health` (or the staging variant)
- **Method**: GET
- **Headers**: `X-LogDate-Health-Token: <token from Secret Manager>`
- **Assertions**:
  - `status` Equal `200`
  - `header content-type` Contains `application/json`
  - `textBody` Contains `"db_connected":true`

Together these catch: wrong status code, server returning HTML/text instead
of JSON, server returning 200 but the database is unreachable, *and* the
monitor's token going stale (the third assertion fails the moment
`db_connected` falls out of the response).
