# Handle resolution runbook

This runbook covers the tasks an operator performs for logdate.app handle resolution: rolling it out
to an environment, checking that a handle resolves, giving older accounts their identities, and
finding accounts that already hold a newly reserved name. [Handle resolution](../explanation/handle-resolution.md)
explains how the system works, and the [handle resolution reference](../reference/handle-resolution.md)
lists the exact commands, responses and settings these steps use.

## Roll out handle resolution

Rolling out handle resolution changes shared infrastructure in Cloudflare, GitHub and Google Cloud,
so an operator runs these steps once for each environment, staging first.

1. Deploy a logdate-web version that includes the `/.well-known/atproto-did` route to staging and
   then to production, following that repository's `docs/workers-migration.md`. Production handles
   start resolving as soon as the production Worker is deployed, because production needs no DNS
   change.
2. Staging needs three changes in the Cloudflare `logdate.app` zone so that requests for staging
   handles reach the staging Worker:
   - Extend the edge certificate that covers `*.staging.logdate.app` so that it also covers
     `*.cloud-staging.logdate.app`.
   - Add a proxied wildcard DNS record named `*.cloud-staging`, with the same target as the
     `*.staging` wildcard.
   - Add the Worker route `*.cloud-staging.logdate.app/*` for `logdate-web-staging`, and make sure
     no Cloudflare Access application covers it.

   Leave the explicit `cloud-staging.logdate.app` record as it is: DNS-only and pointing at Cloud
   Run. The new wildcard record doesn't override it, because an explicit record always takes
   precedence over a wildcard.
3. Create a staging canary account, set the repository variables
   `LOGDATE_STAGING_RESOLUTION_HANDLE` and `LOGDATE_STAGING_RESOLUTION_DID` to its handle and DID,
   and run the **Check Handle Resolution** workflow once by hand to confirm that it passes.
4. Give older accounts their identities in staging and then in production, as described in
   [Give older accounts their identities](#give-older-accounts-their-identities).
5. Check production for accounts that already hold a reserved name, as described in
   [Find accounts that hold a newly reserved name](#find-accounts-that-hold-a-newly-reserved-name),
   and contact the owner of any account it finds.
6. Create a dedicated production canary account. Use an account that belongs to no one, so that the
   monitor doesn't start failing when a person renames or deletes their own account. Add
   `handle_resolution_monitor = { handle = "...", did = "..." }` to
   `infra/terraform/production.tfvars`, apply Terraform for production, and confirm that the uptime
   check turns green in Cloud Monitoring.

## Check that a handle resolves

Check a handle after deploying the server or the website, and whenever the handle resolution alert
fires. The staging workflow only runs automatically on this repository's events, so after deploying
the staging website you also need to run the **Check Handle Resolution** workflow by hand.

1. Run the check against the environment's server. If you know which DID the handle should resolve
   to, also pass `--expect-did <did>`.

   ```bash
   ./run deploy:check-handle --handle <handle> --service-url <server origin> --verify-did-document
   ```

2. Find the first line marked `[FAIL]`; the
   [check table](../reference/handle-resolution.md#handle-check-deploycheck-handle) describes what
   each check requires. The failing check tells you where to look next:
   - If the server check fails, the server either can't find the account or can't complete the
     lookup. Search the server logs for `SERVER_HANDLE_RESOLUTION_FAILED`.
   - If the handle host check fails while the server check passes, the request isn't reaching the
     website. Check the Worker route and the wildcard DNS record in Cloudflare.
   - If the unknown-name check fails, the website is answering for names that nobody owns, which
     points to a fault in the website's route.
   - If the DNS check fails, someone has added an `_atproto` TXT record for the handle. Remove it,
     because it overrides the website's answer.
   - If only the DID document check fails, the handle itself resolves correctly. This failure is
     expected while PLC publishing is off.

## Give older accounts their identities

Accounts created before LogDate supported the AT Protocol have no handle or DID, so their handles
don't resolve until they get an identity. The identity backfill gives every such account one. Run it
only while PLC publishing is off, and never run two backfills at once: when two jobs, or a job and a
sign-in, provision the same account together, one of them creates a DID that is then discarded, and
with publishing on that discarded DID would still reach the public directory.

1. Count the accounts that need an identity:

   ```bash
   ./run deploy:backfill-identities --contract-file <contract> --dry-run
   ```

2. If the count is above zero, run the backfill for real:

   ```bash
   ./run deploy:backfill-identities --contract-file <contract>
   ```

3. If the job exits with a non-zero status, at least one account failed. Search its logs for
   `SERVER_IDENTITY_BACKFILL_ACCOUNT_FAILED`, fix the cause, and run the dry run again. Failed
   accounts still lack an identity, so the dry run counts them again.

## Find accounts that hold a newly reserved name

Adding a name to the reserved list stops new accounts from taking it, but it doesn't change the
handle of an account that already has that name. After reserving new names, check whether any
account already holds one by running this read-only query against the production database, with
the list replaced by the names you added:

```sql
SELECT id, handle, created_at FROM accounts
WHERE split_part(handle, '.', 1) IN ('cloud-staging', 'about', 'contact', 'download', 'support',
      'report', 'security', 'sitemap', 'pricing', 'delete-account', 'privacy', 'terms')
   OR handle LIKE 'xn--%';
```

If the query returns any rows, contact each owner before changing anything. Then move each of them
to a handle with a suffix, as a deliberate and logged identity change. When PLC publishing is on,
that change also publishes a PLC operation. Until an account is moved, the website won't resolve
its handle, so other apps show the handle as unverified.
