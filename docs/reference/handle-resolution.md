# Handle resolution

Every LogDate account has an AT Protocol handle. In production it looks like `name.logdate.app`;
in staging it looks like `name.cloud-staging.logdate.app`. This page explains how other AT Protocol
apps turn that handle into the right account, which system answers them, and how to check that it
keeps working.

## What resolving a handle means

A handle is a friendly name that can change. Underneath it, every account has a DID, a permanent
identifier such as `did:plc:abcdefghijklmnopqrstuvwx`. When an app such as Bluesky meets a handle,
it has to find the DID before it can do anything else. The AT Protocol gives it two places to look:

1. **DNS.** A TXT record at `_atproto.name.logdate.app` whose value is `did=<the DID>`.
2. **HTTPS.** A plain-text file at `https://name.logdate.app/.well-known/atproto-did` whose whole
   body is the DID.

The app then fetches the DID document for that DID and checks that its `alsoKnownAs` list contains
`at://name.logdate.app`. The handle is trusted only when both directions agree: the handle points
at the DID, and the DID document points back at the handle.

## Who answers for logdate.app handles

LogDate uses the HTTPS method. The logdate.app website answers
`https://<handle>/.well-known/atproto-did` for every handle host, and it gets the answer from the
LogDate server. LogDate publishes no DNS records for individual accounts.

Here is what happens when an app resolves `alice.logdate.app`:

1. Cloudflare DNS has a wildcard record for `*.logdate.app`, proxied through Cloudflare. The
   request reaches the website's Cloudflare Worker (`logdate-web-production`) through its
   `*.logdate.app/*` route. The same Worker already serves people's public pages at those hosts.
2. The website route `apps/web/src/app/.well-known/atproto-did/route.ts` in the
   `TheHypertextStudio/logdate-web` repository sees that the host is a single label directly under
   its handle domain (`LOGDATE_HANDLE_DOMAIN`) and is not a reserved name (see
   [Reserved names](#reserved-names)). It asks the server
   `GET https://cloud.logdate.app/xrpc/com.atproto.identity.resolveHandle?handle=alice.logdate.app`.
3. The server looks the handle up with one indexed query and returns the DID, or `HandleNotFound`
   (status 400) when no account with a complete identity owns it. When the lookup itself fails, for
   example because the database is unreachable, the server logs `SERVER_HANDLE_RESOLUTION_FAILED`
   and answers status 500 `InternalServerError` instead.
4. The website returns the DID as `text/plain` with status 200, status 404 for `HandleNotFound`, and
   status 502 for any other server answer or when the server cannot be reached.

At no step is an outage reported as a missing handle, because an app could then treat a real
account as nonexistent. The server is the only authority. The website stores nothing and sends
`Cache-Control: no-store`, so every request reflects the accounts table at that moment.

Resolving a handle only reads. An anonymous request never creates or publishes an identity, and a
lookup for a made-up name costs the same single query as any other.

### Why the website answers instead of the server

We considered three designs.

- **The server answers `*.logdate.app` directly.** The server already has a
  `/.well-known/atproto-did` route that reads the Host header. Getting handle traffic to it would
  mean routing one path of every `*.logdate.app` host to Cloud Run. Cloud Run domain mappings accept
  exact hostnames only, so this needs either a Google load balancer with a wildcard certificate or
  Cloudflare rules that rewrite the Host header for one path. Two systems would then answer the
  same hostname, split by path, in configuration that lives outside both repositories.
- **DNS TXT records per account.** The server would need a Cloudflare API token and would have to
  write a record on every signup and remove it on every deletion. That is a second copy of account
  state that can drift from the database, and DNS changes take time to spread.
- **The website forwards to the server.** The website already owns every `*.logdate.app` host and
  already forwards DID documents the same way. Adding one route keeps a single owner per hostname,
  needs no DNS or load balancer changes in production, and leaves the database as the only source of
  truth. This is the design we use.

The server's own `/.well-known/atproto-did` route stays. It is what a self-hosted LogDate server
uses when its handle domain points straight at it. In LogDate's own deployments that route receives
no handle traffic.

## Reserved names

Every handle is also a host on the website, so some labels can never be handles: the hosts other
systems own (`cloud`, `api`, `cloud-staging`, `mail`, ...), the website's own hosts (`www`, `app`),
and the names of its public pages (`about`, `pricing`, `delete-account`, ...). The server's list is
`HandleLabels.RESERVED` in `server/src/main/kotlin/app/logdate/server/identity/HandleLabels.kt`,
and it must include every entry of `RESERVED_SUBDOMAINS` in logdate-web's
`apps/web/src/lib/site.ts`. A test pins that.

The server also refuses labels starting with `xn--`. Those are punycode, which browsers display as
non-ASCII text, so they could imitate another person's handle.

The server applies both rules when someone signs up, changes their username, or creates an account
through `com.atproto.server.createAccount`. A username maps to its label by lowercasing it and
turning `_` into `-`, so `delete_account` and `xn__name` are refused too. The website refuses
reserved labels and its own hostnames before asking the server anything.

These rules only stop new names. They do not change an existing account's handle. To check whether
anyone already holds a newly reserved name, an operator can run this read-only query against the
production database:

```sql
SELECT id, handle, created_at FROM accounts
WHERE split_part(handle, '.', 1) IN ('cloud-staging', 'about', 'contact', 'download', 'support',
      'report', 'security', 'sitemap', 'pricing', 'delete-account', 'privacy', 'terms')
   OR handle LIKE 'xn--%';
```

If it returns rows, contact each owner before changing anything, then move them to a suffixed handle
as a deliberate, logged identity change. That change publishes a PLC operation when publishing is
on. Until then, the website does not resolve those names, so the affected handles show as
unverified elsewhere.

## Staging

Staging keeps two namespaces apart. Its website runs at `staging.logdate.app` and
`*.staging.logdate.app`, behind Cloudflare Access so only staff can open it. Its handles live under
`cloud-staging.logdate.app` (`ATPROTO_HANDLE_DOMAIN` in `infra/terraform/staging.tfvars`), so an
account's handle is `name.cloud-staging.logdate.app`.

The website route answers for handle hosts under its handle domain even when they are outside its
browser namespace, so the staging Worker can answer for `*.cloud-staging.logdate.app` once traffic
reaches it. That needs three Cloudflare changes, listed under [Applying the changes](#applying-the-changes).
Handle hosts must not sit behind Cloudflare Access, because outside apps cannot sign in. The
explicit `cloud-staging.logdate.app` record stays DNS-only and keeps pointing at Cloud Run; a
wildcard record never overrides an explicit one.

## When an account changes

- **Signup.** The server assigns the handle when it creates the account's identity, and the handle
  resolves on the next request.
- **Handle change.** Resolution reads the current handle on every request, so the old handle stops
  resolving and the new one starts at once. LogDate has no screen for changing a handle today;
  changing a username does not change the handle.
- **Deletion.** Deleting an account removes its row, the server returns `HandleNotFound`, and the
  website returns 404.
- **Old accounts without an identity.** Accounts created before AT Protocol identities existed have
  no handle or DID, so their handles do not resolve. They get an identity the next time their owner
  signs in, or when an operator runs the backfill described below. Resolving a handle never assigns
  one. Within one server process, two requests that provision the same account at once wait for
  each other, so they never create two DIDs. The lock does not span server instances.

Other apps keep their own caches. Bluesky, for example, remembers a resolved handle for hours, so a
deleted handle can keep appearing there for a while even though LogDate stopped answering for it.

`did:web` identities have one more delay. The website's per-handle `/.well-known/did.json` proxy
caches DID documents for 5 minutes and may serve a stale copy for up to an hour while it refreshes.
After a handle changes or an account is deleted, a `did:web` document can keep naming the old handle
for up to an hour. Hosted accounts use `did:plc` by default, so this only affects deployments that
set `ATPROTO_HOSTED_DID_METHOD=web`.

### Backfilling old accounts

`./run deploy:backfill-identities --contract-file <contract> --dry-run` runs
`scripts/identity-backfill-job.py`. It renders the environment's deployment contract with
`scripts/render-cloud-run-contract.sh` first. The script then runs the server image once as a Cloud
Run job with `--backfill-identities`, against that environment's own database and secrets.

- With `--dry-run`, the job only counts. Its log says
  `Identity backfill dry run: N accounts need an identity`.
- Without it, the job provisions each account the way sign-in does and logs
  `Identity backfill complete: N provisioned, M failed`. It exits non-zero if any account failed.
  Running it again is safe, because it only picks up accounts that still lack an identity.
- While the server publishes PLC operations (`ATPROTO_PLC_PUBLISH_ENABLED=true`), every new identity
  goes to the public PLC directory. A real run then refuses to start
  (`SERVER_IDENTITY_BACKFILL_REFUSED_PUBLISHING`) unless `--allow-plc-publishing` is given. With
  publishing off, nothing leaves the server.

## Checking that it works

`./run deploy:check-handle --handle <handle> --service-url <server origin>` runs
`scripts/check-handle-resolution.sh`. It asks the server, then asks the handle host the way an
outside app would, and reports each step as `[PASS]` or `[FAIL]`:

- the server resolves the handle (`--expect-did` pins the exact DID);
- `https://<handle>/.well-known/atproto-did` returns that same DID with status 200. The AT Protocol
  allows a handle host to redirect, but LogDate's hosts always answer directly. This check is
  stricter than the protocol and reports a redirect as a failure, because one means the request hit
  something else, such as a sign-in wall;
- a random label under the same domain returns 404, which proves the website is not answering for
  names nobody owns;
- `_atproto.<handle>` has no TXT record, or one that agrees, because a stray record would override
  the HTTPS answer;
- with `--verify-did-document`, the DID document lists `at://<handle>`.

Staging runs this check through `.github/workflows/check-handle-resolution.yml` after every
successful staging deploy and once a day. It resolves the account named by the repository variable
`LOGDATE_STAGING_RESOLUTION_HANDLE` and, when `LOGDATE_STAGING_RESOLUTION_DID` is set, requires that
exact DID. The server origin comes from `LOGDATE_PUBLIC_ORIGIN` in `infra/terraform/staging.tfvars`.
The job is skipped until the handle variable exists. The workflow runs only on this repository's
events. A website deploy that breaks staging resolution is not caught until the next daily run, so
run the workflow by hand after deploying the staging website.

Production uses a Cloud Monitoring uptime check defined in `infra/terraform/main.tf`. When
`handle_resolution_monitor` names a canary account's handle and DID in `production.tfvars`, Cloud
Monitoring fetches `https://<handle>/.well-known/atproto-did` every five minutes from several
regions and requires the body to be exactly that DID. If the check keeps failing for ten minutes,
the alert policy notifies the operator email channel from `cleanup_operator_email` and any channels
in `handle_resolution_notification_channels`. Terraform refuses to create the alert when neither is
set. The alert's runbook names the environment's own `LOGDATE_PUBLIC_ORIGIN` as the server to check. Use a dedicated canary account rather than a person's
account, so the monitor does not break when someone deletes or renames their own account.

## Known limit: the DID document

Handle resolution is half of the check that other apps perform. Hosted accounts receive `did:plc`
identities, and the server sends their operations to `plc.directory` only when
`ATPROTO_PLC_PUBLISH_ENABLED` is `true`. Until then, apps can resolve a handle to its DID but cannot
fetch the DID document, so they still show the handle as unverified. Run the check script with
`--verify-did-document` to see this step.

## Applying the changes

The code is in place. These steps change shared infrastructure, so an operator runs them:

1. Merge the logdate-web change
   ([TheHypertextStudio/logdate-web#29](https://github.com/TheHypertextStudio/logdate-web/pull/29))
   and deploy it to staging, then to production, using the steps in that repository's
   `docs/workers-migration.md`. Production handle resolution starts working as soon as
   the production Worker is deployed; it needs no DNS change.
2. For staging, in the Cloudflare `logdate.app` zone:
   - extend the edge certificate that covers `*.staging.logdate.app` to also cover
     `*.cloud-staging.logdate.app`;
   - add a proxied wildcard DNS record `*.cloud-staging` with the same target as the
     `*.staging` wildcard;
   - add the Worker route `*.cloud-staging.logdate.app/*` for `logdate-web-staging`, with no
     Cloudflare Access application covering it.
3. Create a staging canary account, set the repository variables `LOGDATE_STAGING_RESOLUTION_HANDLE`
   and `LOGDATE_STAGING_RESOLUTION_DID`, and run the **Check Handle Resolution** workflow by hand
   once.
4. Run the identity backfill with `--dry-run` in staging and then in production. If it reports
   accounts, run it again without `--dry-run`, while PLC publishing is off.
5. Run the reserved-name query under [Reserved names](#reserved-names) against production. Follow up
   with the owner of any account it finds.
6. Create a production canary account, add `handle_resolution_monitor = { handle = "...", did =
   "..." }` to `infra/terraform/production.tfvars`, and apply Terraform for production. Confirm the
   uptime check turns green in Cloud Monitoring.
