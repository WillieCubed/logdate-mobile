# Handle resolution reference

This page lists the exact behaviour of logdate.app handle resolution: the domains handles live
under, what each endpoint returns, which names can become handles, how account state affects
resolution, and the commands and monitors operators use. For how and why it works this way, see
[Handle resolution](../explanation/handle-resolution.md). For step-by-step tasks, see the
[handle resolution runbook](../runbook/handle-resolution.md).

## Handle domains

Each environment gives handles their own domain. The server reads it from `ATPROTO_HANDLE_DOMAIN`
and the website from `LOGDATE_HANDLE_DOMAIN`. In production, handles look like `name.logdate.app`.
In staging, they look like `name.cloud-staging.logdate.app`, and the server's setting lives in
`infra/terraform/staging.tfvars`.

## Endpoints

Two endpoints answer a handle lookup: the website answers outside apps, and the server answers the
website.

### Website: `GET https://<handle>/.well-known/atproto-did`

The website answers this request from the route
`apps/web/src/app/.well-known/atproto-did/route.ts` in `TheHypertextStudio/logdate-web`, which runs
in the `logdate-web-production` Worker through its `*.logdate.app/*` route. It only answers for a
host that is a single label directly under the handle domain and is not a reserved name. For such a
host it asks the server `GET <server>/xrpc/com.atproto.identity.resolveHandle?handle=<handle>` and
translates the answer as follows, always with `Cache-Control: no-store`:

| Server answer | Website response |
|---|---|
| The server returns the DID. | The website returns status 200 with a `text/plain` body containing only the DID. |
| The server returns `HandleNotFound` (status 400). | The website returns status 404. |
| The server returns anything else, or can't be reached. | The website returns status 502. |

### Server: `com.atproto.identity.resolveHandle`

The server answers with a single indexed query that never writes:

| Case | Response |
|---|---|
| An account with a handle, a DID and a signing key owns the handle. | The server returns status 200 with the DID. |
| No such account exists. | The server returns status 400 with `HandleNotFound`. |
| The lookup fails, for example because the database is unreachable. | The server returns status 500 with `InternalServerError` and logs `SERVER_HANDLE_RESOLUTION_FAILED`. |

## Naming rules

The server checks every new handle against the rules below on signup and on username changes. The
rules only stop new names; they never change a handle an account already has.

### Reserved labels

A handle label can't be a name in `HandleLabels.RESERVED`
(`server/src/main/kotlin/app/logdate/server/identity/HandleLabels.kt`), and it can't start with
`xn--`. The website refuses the same reserved labels, plus its own hostnames, before it asks the
server anything.

The server's list must include every entry of `RESERVED_SUBDOMAINS` in logdate-web's
`apps/web/src/lib/site.ts`. This repository's build can't read the website's source, so
`HandleLabelsTest` keeps a hand-copied list of the website's entries and fails when the server
misses one. When you add a name to either list, add it to the other list and to the test's copy in
the same change.

### Username-to-label mapping

The label checked against those rules comes from the username:

1. Lowercase the username.
2. Replace each run of characters other than `a-z`, `0-9` and `-` (including `_`) with a single `-`.
3. Trim hyphens from both ends.

So `delete_account` becomes `delete-account`, which is reserved. The server also checks the
username with each `_` read as `-`, so `xn__name` is refused even though the mapping turns it into
`xn-name`.

## Account states

Whether a handle resolves depends on the state of the account that holds it:

| Account state | Result |
|---|---|
| The account has a handle, a DID and a signing key. | The handle resolves. |
| The account was created before AT Protocol identities and lacks any of the three. | The handle doesn't resolve, because lookups by handle and by DID both skip the account. |
| The account was deleted. | The handle doesn't resolve: the server answers `HandleNotFound` and the website answers 404. |

A handle change takes effect on the next request. LogDate has no screen for changing a handle, and
changing a username doesn't change the handle.

### Identity provisioning

An account without an identity gets one when the person signs in by username or email, or when the
identity backfill reaches it. Resolving a handle never provisions one. Signing in by handle with
`com.atproto.server.createSession` is not an option, because that method refuses every password.

Provisioning never gives one account two DIDs. Within one server process, it re-reads the account
under a lock. Across instances, it writes the identity with a conditional update that succeeds only
while the stored identity is unchanged, so it never writes back a deleted account either. The
request that loses the race has still created a DID of its own, which nothing uses; with PLC
publishing on, that unused DID also reaches the public directory.

### `did:web` document caching

Deployments that set `ATPROTO_HOSTED_DID_METHOD=web` have one more delay. The website's per-handle
`/.well-known/did.json` proxy caches DID documents for 5 minutes and may serve a stale copy for up
to an hour while it refreshes, so after a handle changes or an account is deleted, its `did:web`
document can keep naming the old handle for up to an hour.

## Commands

Operators use two commands: one checks a handle the way an outside app would, and the other gives
old accounts their identities.

### Handle check: `deploy:check-handle`

`./run deploy:check-handle` runs `scripts/check-handle-resolution.sh`:

```bash
./run deploy:check-handle --handle <handle> --service-url <server origin> [--expect-did <did>] [--verify-did-document]
```

It runs the checks below in order and prints `[PASS]` or `[FAIL]` for each:

| Check | When it passes |
|---|---|
| Server | The server resolves the handle, and to the DID given with `--expect-did` if there is one. |
| Handle host | `https://<handle>/.well-known/atproto-did` returns the same DID with status 200. A redirect counts as a failure: the protocol allows one, but LogDate's hosts always answer directly, so a redirect means something else, such as a sign-in wall, intercepted the request. |
| Unknown name | A random label under the same domain returns status 404. |
| DNS | `_atproto.<handle>` has no TXT record, or has one with the same DID. A stray record would override the HTTPS answer. |
| DID document | This check runs only with `--verify-did-document`, and passes when the DID document lists `at://<handle>`. |

### Identity backfill: `deploy:backfill-identities`

`./run deploy:backfill-identities` runs `scripts/identity-backfill-job.py`:

```bash
./run deploy:backfill-identities --contract-file <contract> [--dry-run] [--allow-plc-publishing]
```

The script renders the environment's deployment contract with
`scripts/render-cloud-run-contract.sh`, then runs the server image once as a Cloud Run job with
`--backfill-identities`, against that environment's database and secrets. The job only selects
accounts that still lack an identity, so running it again is safe. Its log and exit status depend on
the mode:

| Mode | Log line | Exit status |
|---|---|---|
| `--dry-run` | `Identity backfill dry run: N accounts need an identity` | The job exits with status 0. |
| A real run, without `--dry-run` | `Identity backfill complete: N provisioned, S skipped, M failed` | The job exits with a non-zero status if any account failed. |

An account deleted after the job listed it is counted as skipped and stays deleted. Each failed
account logs `SERVER_IDENTITY_BACKFILL_ACCOUNT_FAILED` without an account ID, because production
logs never name an account; it still lacks an identity, so the next dry run counts it again. While
`ATPROTO_PLC_PUBLISH_ENABLED=true`, a real run refuses to start
(`SERVER_IDENTITY_BACKFILL_REFUSED_PUBLISHING`) unless `--allow-plc-publishing` is given.

## Monitoring

Each environment watches a canary account's handle: staging with a GitHub workflow, and production
with a Cloud Monitoring uptime check.

### Staging workflow

`.github/workflows/check-handle-resolution.yml` runs the handle check after every successful staging
deploy and once a day, and can also be run by hand. It only runs on this repository's events, so a
website deploy that breaks resolution is caught by the next daily run. It reads these settings:

| Setting | Meaning |
|---|---|
| `LOGDATE_STAGING_RESOLUTION_HANDLE` | This repository variable holds the canary handle. The job is skipped until it exists. |
| `LOGDATE_STAGING_RESOLUTION_DID` | This optional repository variable holds the exact DID the handle must resolve to. |
| `LOGDATE_PUBLIC_ORIGIN` | This value in `infra/terraform/staging.tfvars` is the server origin the workflow checks. |

### Production uptime check

Production's uptime check is defined in `infra/terraform/main.tf`. Cloud Monitoring fetches
`https://<handle>/.well-known/atproto-did` every five minutes from several regions and requires the
body to be exactly the canary's DID. After ten minutes of failures, an alert notifies its channels;
its text names the environment's `LOGDATE_PUBLIC_ORIGIN` as the server to check. These Terraform
variables configure it:

| Terraform variable | Meaning |
|---|---|
| `handle_resolution_monitor` | This holds `{ handle, did }` for a dedicated canary account. When it is null, no monitor is created. |
| `cleanup_operator_email` | The alert notifies this operator email channel. |
| `handle_resolution_notification_channels` | The alert also notifies these channels. Terraform refuses to create the alert when neither this nor the email is set. |
