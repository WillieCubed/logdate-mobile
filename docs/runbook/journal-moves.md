# Journal moves runbook

This runbook covers the tasks an operator performs when existing journals move into personal
spaces: turning moves on in staging, rehearsing a move with a realistic journal, watching moves
while they run, switching an account back, and stopping moves. [Moving journals into personal
spaces](../explanation/journal-moves.md) explains how a move works, and the [journal moves
reference](../reference/journal-moves.md) lists every setting, code, metric and log message these
steps mention.

## Turn on moves in staging

A move changes where an account's journal lives, so it is rehearsed in staging before any
production account moves. Turning it on takes a server change and an app change.

1. Confirm that the staging database has the migration that adds `space_migrations`
   (`V50__Add_space_migrations.sql`). The Deploy Server Staging workflow runs migrations before it
   deploys, so any staging deploy of a revision that contains it is enough.
2. Staging already runs with `LOGDATE_PERSONAL_SPACE_ENABLED = "true"`. Add
   `LOGDATE_PERSONAL_SPACE_MIGRATION_ENABLED = "true"` next to it in `cloud_run_env` in
   `infra/terraform/staging.tfvars` and in `infra/terraform/staging-contract-template.json`, and
   deploy staging. The deployment contract renderer accepts the flag in every environment.
3. Check that the server offers moves. Its description should list `personalSpaceMigrationV1`:

   ```bash
   curl -s https://cloud-staging.logdate.app/api/v1/server/info | jq '.protocolFeatures'
   ```

4. Install a staging build of the app on each test device. On the device that will move the
   journal, open Settings, Developer tools, and turn on Upgrade existing journals under Sealed
   storage previews, which turns on Personal space sync as well. A device with both switches off
   never moves a journal, which makes it the device that plays an older app in the rehearsal.

## Rehearse a move with a realistic journal

A rehearsal is only useful when the journal looks like a real one: thousands of entries, photos,
recordings, drafts, marked moments, location history and journals that share entries. The quickest
way to get one is a tester's own export archive, which keeps the data inside the tester's own account.

1. Create a staging account for the rehearsal. Staging creates new accounts in their personal
   spaces, so before the account holds anything, put it on legacy storage, the way every account
   created before personal spaces is:

   ```sql
   UPDATE accounts SET storage_mode = 'legacy' WHERE username = '<tester username>';
   ```

   Only do this to an account that has never synced, because it changes which store the account's
   devices read.
2. Sign in to that account on a phone with both switches off, import the tester's export archive
   from Settings, and let the phone sync until the Backup page says everything is backed up. Sign
   the same account in on a second device with both switches off and let it download everything;
   this device writes during the move later.
3. Turn on moves as described above, then turn on Upgrade existing journals on the phone. Its next
   sync starts the move, and the Backup page shows Upgrading to sealed storage with a count of
   items uploaded.
4. While the phone uploads, write an entry and attach a photo on the second device. Both reach legacy
   storage, and the phone includes them when it checks.
5. Close the app on the phone partway through the upload and open it again. The count resumes from
   where it stopped instead of starting over.
6. When the Backup page shows Sealed storage, check the account from the server side. The account's
   storage mode is `personalSpaceV1`, and its row in `space_migrations` is `switched`:

   ```sql
   SELECT a.storage_mode, m.phase, m.switched_at, m.switched_at_seq
   FROM space_migrations m JOIN accounts a ON a.id = m.account_id
   WHERE a.username = '<tester username>';
   ```

7. On the second device, which still has the switches off, sync again. Sync pauses and the Backup
   page says to update LogDate to keep syncing. Turn on Personal space sync and sync: the device
   settles onto the personal space and shows the entry and photo it wrote earlier.
8. Compare the journal on both devices with the tester's archive: entry counts in Timeline, a few
   photos and recordings opened in full, the drafts, the marked moments and a day of location
   history.
9. Switch back from the phone, as described in [Switch an account back](#switch-an-account-back),
   and check that both devices return to legacy storage with everything intact, including an entry
   written after the switch.

Record the rehearsal in the release notes for the build that turns moves on: the journal's size, how
long the upload and the check took, and anything that stopped the move along the way. A move that
stopped and recovered on its own is worth recording too, because it shows how a real account behaves.

## Watch moves while they run

Moves are quiet when they work, so watching them means checking a few signals each day while moves
are turned on.

The alert `LogDateSpaceMigrationServerErrors` fires when the move calls answer with server errors for
15 minutes. Devices stop and retry, so no account is harmed, but no account switches either until the
cause is fixed; start with the server logs around the first error. The alert
`LogDateLegacyCopyCleanupFailures` fires after an hour of failed cleanup runs; legacy copies stay in
place until the cleanup succeeds, so the cost is storage, not data.

The production log codes tell you what moves are doing. A steady trickle of
`SERVER_SPACE_MIGRATION_SWITCHED` is the expected sign of progress. Any
`SERVER_SPACE_MIGRATION_COPY_MISMATCH` means the server read different bytes back from storage than it
copied, which points at the storage bucket, not the app. `SERVER_LEGACY_COPY_CLEANUP_SKIPPED` means a
move recorded as switched belongs to an account that isn't in its personal space, which should never
happen; leave that account's copy alone and look at its row in `space_migrations` and its
`storage_mode` in `accounts` before doing anything else.

The `space_migrations` table shows where every move stands. This query counts moves by phase and
lists those that have been uploading for more than a day, which usually means a device stopped with a
problem the person hasn't acted on:

```sql
SELECT phase, count(*) FROM space_migrations GROUP BY phase;

SELECT account_id, started_at, updated_at FROM space_migrations
WHERE phase = 'uploading' AND updated_at < now() - interval '1 day';
```

A move that stopped on a device says why on that device's Backup page. The person can fix most
problems themselves by syncing, freeing storage or entering their recovery phrase, and the move tries
again on its own.

## Switch an account back

Switching back returns an account to legacy storage, with every change made since the switch, for 30
days after it moved. Only one of the account's devices can do it, because only a device can read the
personal space: open Settings, the Backup page, and choose Switch back to the previous storage. The
device downloads the personal space, holds writes to it, queues every change since the switch for
legacy storage and asks the server to switch. The Backup page shows Previous storage when it is done,
and the account's other devices follow on their next sync.

After the 30 days the legacy copy is gone and switching back is refused with
`SPACE_MIGRATION_WINDOW_CLOSED`. The journal is then only in the personal space, and an export archive
from a device is the way to take it elsewhere.

## Stop moves

Stopping moves is safe at any point, because an account either switched or it didn't. Set
`LOGDATE_PERSONAL_SPACE_MIGRATION_ENABLED` back to `false` and deploy. The server stops offering
moves, devices that were uploading stop with a refusal and their accounts stay on legacy storage, and
any hold on legacy writes lapses within 30 minutes.

Accounts that already switched stay in their personal spaces. Their devices keep syncing there, and
the legacy routes keep telling older apps to update. Turning moves off also turns off switching back,
because the switch-back call is part of the same feature, so switch back any account that needs it
before you deploy the change. Their legacy copies are still deleted 30 days after each switch. To keep
those copies longer while you investigate something, set `SYNC_TOMBSTONE_PURGE_ENABLED=false`, which
pauses the whole maintenance loop, tombstone purges included, until you set it back.
