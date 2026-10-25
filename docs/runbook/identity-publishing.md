# Identity publishing runbook

This runbook covers the tasks an operator performs to publish LogDate identities to the public PLC
directory: rehearsing the procedure, confirming that production has the required safeguards, turning
publishing on, publishing every existing account, watching the result, and stopping or recovering
when something goes wrong. Publishing cannot be undone, so read all of it before you start.
[Identity publishing](../explanation/identity-publishing.md) explains what publishing means and why
it is irreversible, and the [identity publishing reference](../reference/identity-publishing.md)
lists the command's options, states, exit codes and log codes.

## Rehearse publishing

Rehearse on the exact commit that production runs, on your own machine, before you touch production.
The rehearsal needs Java 17 or later, Node.js 22 or later with corepack, Python 3, curl and the
PostgreSQL server tools, and it only ever talks to addresses on your machine. It starts a throwaway
database, the reference PLC directory and a LogDate server, then follows this runbook with test
accounts, including a bad publish and its recovery.

1. Check out the commit that production runs, and run the rehearsal:

   ```bash
   ./run identity:rehearse
   ```

   Use `--accounts <n>` to change how many existing accounts it creates, and `--skip-server-build`
   to reuse the server build from an earlier run. The first run also downloads and builds the PLC
   directory, which takes a few minutes.

2. Expect it to print `Rehearsal passed` and exit with status 0. If it stops, the last `==>` line
   names the step that failed, and the logs and reports are in
   `tmp/identity-publishing-rehearsal/<run>/`.

## Confirm the safeguards

Publishing makes every account a public identity, so confirm each of these on production on the day
you start. If any one fails, stop and fix it first.

1. Confirm that private reads require sign-in. The repository read endpoints must refuse a request
   that carries no session, for an account that has entries. Each of these four requests must
   answer `401`:

   ```bash
   curl -i "https://cloud.logdate.app/xrpc/com.atproto.sync.getRepo?did=<your DID>"
   curl -i "https://cloud.logdate.app/xrpc/com.atproto.repo.listRecords?repo=<your handle>&collection=app.logdate.entry"
   curl -i "https://cloud.logdate.app/xrpc/com.atproto.repo.getRecord?repo=<your handle>&collection=app.logdate.entry&rkey=<a record key>"
   curl -i "https://cloud.logdate.app/xrpc/com.atproto.sync.getBlob?did=<your DID>&cid=<a blob CID>"
   ```

2. Confirm that signing keys and sessions have their own secrets. Account signing keys are
   encrypted with `ATPROTO_SIGNING_KEY_KEK` and hosted sessions are signed with
   `ATPROTO_SESSION_SECRET`, and the server refuses to start in production when either is missing
   or equal to `JWT_SECRET`. Check that both are bound to their own Secret Manager secrets at exact
   versions, and that the running revision started with them:

   ```bash
   grep -n 'ATPROTO_SIGNING_KEY_KEK\|ATPROTO_SESSION_SECRET\|JWT_SECRET' infra/terraform/production.tfvars
   gcloud run services describe <service> --region <region> --project <project> \
       --format='value(status.conditions[0].status)'
   ```

   Expect each of the two to name its own `secret_id`, a different one from `JWT_SECRET`'s, and
   expect the service condition to read `True`.

3. Confirm that the OAuth signing key persists. Note the `kid` that production serves, deploy or
   restart a revision, and expect the same `kid` afterwards:

   ```bash
   curl -s https://cloud.logdate.app/oauth/jwks | jq -r '.keys[].kid'
   ```

4. Confirm that handle resolution works. Check your own handle against the production server, and
   expect every line to be `[PASS]`, with only the DID document check allowed to fail while publishing
   is still off:

   ```bash
   ./run deploy:check-handle --handle <your handle> --service-url https://cloud.logdate.app --verify-did-document
   ```

   Then ask an outside resolver, and expect it to return your DID:

   ```bash
   curl "https://public.api.bsky.app/xrpc/com.atproto.identity.resolveHandle?handle=<your handle>"
   ```

5. Confirm that people can hold their own recovery key. The server's descriptor must list
   `atprotoRecoveryKeysV1`, which means the server was started with `ATPROTO_RECOVERY_KEYS_ENABLED`
   set to `true` in the deployment contract's `env_vars`:

   ```bash
   curl -s https://cloud.logdate.app/api/v1/server/info | jq '.data.protocolFeatures'
   ```

   Then register a recovery key from the app on a test account, and check that it appears first in
   the identity's rotation keys. If LogDate's key is listed first, LogDate can overrule the person,
   and the safeguard is not met.

6. Confirm that sign-in is passkey-only. Both of these requests must be refused on production, even
   for an account that once had a password:

   ```bash
   curl -i -X POST https://cloud.logdate.app/xrpc/com.atproto.server.createSession \
       -H 'content-type: application/json' -d '{"identifier":"<handle>","password":"x"}'
   curl -i -X POST https://cloud.logdate.app/xrpc/com.atproto.server.createAccount \
       -H 'content-type: application/json' -d '{"handle":"<new handle>","password":"x"}'
   ```

   Expect `400` with `{"error":"InvalidRequest","message":...}` from each, and expect no account
   to exist for the new handle afterwards.

7. Confirm that public repository commits follow the specification. Run the repository tests, which
   export repositories and verify them with the pinned reference `@atproto/repo` library, on the
   commit that production runs:

   ```bash
   ./gradlew :shared:atproto-repo:jvmTest
   ```

   Then download a test account's repository from production and verify it with
   `tools/atproto-repo-verify/verify.mjs` against the signing key in the account's DID document.

8. Confirm that PLC operations are valid. The rehearsal sends real operations to the reference
   directory, which refuses any with a bad signature or the wrong signing key, so a passing
   rehearsal on the deployed commit is the check. Run the server's identity tests as well:

   ```bash
   ./gradlew :server:test
   ```

## Turn publishing on

Turning publishing on changes only what happens to identities created from then on. Do these steps
in order, and read each result before you continue.

1. Give older accounts their identities while publishing is still off, as described in
   [Give older accounts their identities](handle-resolution.md#give-older-accounts-their-identities).
   Never run two backfills at once.
2. Render the production deployment contract for the release that is deployed:

   ```bash
   ./scripts/render-cloud-run-contract.sh --environment production --release-sha <deployed release SHA> \
       > <contract file>
   ```

3. Check where every identity stands, and expect exit status 3 and a summary that counts only
   `unpublished`, `not_provisioned`, `published` and `not_plc` identities. Any `missing_genesis`,
   `incomplete` or `tombstoned` count needs an explanation before you continue, because the command
   cannot publish those accounts:

   ```bash
   ./run identity:publish-job status --contract-file <contract file>
   ```

4. Run a dry run. Its `would_publish_genesis` and `would_provision_and_publish` counts are how many
   identities the real run will send:

   ```bash
   ./run identity:publish-job dry-run --contract-file <contract file>
   ```

5. Add `ATPROTO_PLC_PUBLISH_ENABLED = "true"` to `cloud_run_env` in
   `infra/terraform/production.tfvars` and deploy through the normal tag-gated production release.
   Only production may set it, because the contract renderer refuses it for staging. When the new
   revision serves traffic, the server descriptor lists `atprotoPlcPublishingV1` and new sign-ups
   log `SERVER_PLC_OPERATION_PUBLISHED`.

## Publish every existing account

Publish in three stages, so that a mistake affects one account before it can affect many. Render a
new contract for the release that now runs, and use it for every command below.

1. Publish your own account and look at it. Expect exit status 0:

   ```bash
   ./run identity:publish-job publish --contract-file <contract file> \
       --confirm-directory https://plc.directory --handle <your handle>
   ```

   Then open `https://plc.directory/<your DID>`, and check that your handle is in `alsoKnownAs` and
   that `https://cloud.logdate.app` is the `#atproto_pds` service. Check that the outside resolver
   from the safeguards returns your DID, and that your journal still works in the app.

2. Publish a small batch of the oldest accounts. Expect exit status 0, and read the summary lines
   and the monitoring described below:

   ```bash
   ./run identity:publish-job publish --contract-file <contract file> \
       --confirm-directory https://plc.directory --limit 10
   ```

3. Publish the rest. At the default pace of 30 operations a minute, 1,000 identities take about 35
   minutes, so raise `--timeout-seconds` for larger runs. If the run stops at its failure limit,
   read [Recover from a bad publish](#recover-from-a-bad-publish) before you run it again:

   ```bash
   ./run identity:publish-job publish --contract-file <contract file> \
       --confirm-directory https://plc.directory
   ```

4. Confirm the result by running `status` again. Expect exit status 0 and every count to be
   `published` or `not_plc`. Run it once more a day later, to catch accounts that signed up between
   the first status check and the deployment.

## Monitor publishing

Make a log-based metric for each of the codes below, so that they can be graphed and alerted on.
The [reference](../reference/identity-publishing.md#log-codes) describes every code.

1. Watch `SERVER_PLC_OPERATION_PUBLISHED` as the success count. It should rise by one for each new
   sign-up and for each identity a run publishes.
2. Alert on `SERVER_PLC_OPERATION_REJECTED`. The directory refused an operation as invalid, so stop
   any run in progress and reproduce the problem with the rehearsal, whose report files contain the
   directory's own explanation.
3. Alert on `SERVER_PLC_DIRECTORY_RATE_LIMITED`. Lower `--operations-per-minute` and run again.
4. Alert on `SERVER_PLC_DIRECTORY_UNREACHABLE`. Check the directory's status page, and expect new
   sign-ups to fail until it answers, because a new identity must be sent before sign-up finishes.
5. Watch `SERVER_PLC_OPERATION_UNCONFIRMED`. Run `status` later, and expect the identity to become
   `published` or `published_unrecorded`.
6. Read the `SERVER_IDENTITY_PUBLISHING_*` summary lines after every run, and treat
   `SERVER_IDENTITY_PUBLISH_FAILED` and `SERVER_IDENTITY_PUBLISHING_STOPPED` as a reason to stop and
   read them.
7. Watch the sign-up error rate while publishing is on, because a directory outage appears there
   first.

## Stop publishing

Stopping at any point is safe. An account whose genesis operation was sent but not yet recorded
shows up as `published_unrecorded` next time and is recorded without sending anything again.

1. To stop a run in progress, cancel its execution:

   ```bash
   gcloud run jobs executions cancel <execution> --region <region> --project <project>
   ```

2. To stop publishing altogether, set `ATPROTO_PLC_PUBLISH_ENABLED = "false"` in
   `infra/terraform/production.tfvars` and deploy. New identities are then signed and stored but not
   sent, the `publish` command refuses to run while `status` keeps working, and the server stops
   advertising `atprotoPlcPublishingV1`, so apps hide key rotation and the server refuses it.
   Identities that were already published stay published, because nothing removes them.

3. To start again, turn the setting back on and run the command again, which publishes whatever was
   created in between.

## Recover from a bad publish

Match what you see to one of these cases, and fix the cause before you run `publish` again.

1. If the directory rejected operations, nothing was published for those accounts, because a
   rejected operation is never recorded, and the run stopped at its failure limit. Production logs
   do not say why, so run `./run identity:rehearse` on the deployed commit. If it passes, compare
   the rehearsal's settings with production, starting with the handle domain, the PDS address and
   the key algorithm. Run `publish` again after you fix the cause.

2. If identities went out with wrong details, such as a wrong PDS address from a wrong
   `ATPROTO_PDS_SERVICE_URL`, correct the setting and deploy it. `status` then reports those
   identities as `mismatch`, and its report file says which part differs. Send corrected updates,
   which LogDate's key signs, and then confirm that every identity matches:

   ```bash
   ./run identity:publish-job publish --contract-file <contract file> \
       --confirm-directory https://plc.directory --reconcile
   ./run identity:publish-job status --contract-file <contract file>
   ```

3. If a run stopped on a rate limit or an outage, wait, lower `--operations-per-minute` when the
   directory limited you, and run `publish` again. Accounts that were already published are skipped.

4. If an identity was published that should not exist, such as a test account, it cannot be
   removed. The only option is a tombstone, which deactivates the identity permanently, and the
   command does not create one. Decide with the account's owner, or with the team for a test
   account, before anyone signs one by hand.

5. If an identity shows `controlled_elsewhere`, its owner has used their own rotation key to take
   it over. This is the system working as designed, so leave it alone. The command never touches
   these identities.
