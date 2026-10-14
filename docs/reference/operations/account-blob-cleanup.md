# Account blob cleanup

Deleting an account writes its media, AT Protocol blob, and encrypted backup paths to a PostgreSQL
outbox in the same transaction as the account cascade. If that write fails, the account deletion
fails. Storage failures after commit leave durable work for retry.

The server attempts cleanup immediately. The hourly Cloud Run job runs the same image with
`--cleanup-account-blobs`, without starting HTTP. It leases pending owners and treats an already
missing object as success. Exact owner namespace scans continue for at least 24 hours to collect
uploads that were already in flight when the account was removed. This window exceeds Cloud Run's
maximum 60-minute request timeout; other hosts must bound upload lifetimes below the reconciliation
window as well. Active accounts are never
cleaned. A minimal deleted-account identifier remains to prevent reuse of a namespace; successful
object paths are removed from the outbox.

Apply the Terraform job, OAuth Scheduler invocation, and Monitoring policies before relying on
scheduled recovery. Configure `cleanup_notification_channels` with monitored Cloud Monitoring
channel resource names. Deployment renders the job from the server's immutable contract so the
image, database secret versions, storage settings, and runtime identity match. The deployment
executes the job once and fails if it cannot complete its batch. Apply V40 before the new server;
production startup and the migration helper reject missing or disabled cleanup triggers.

A failed execution or queue older than 24 hours is a P1 requiring investigation within one business
day. Inspect the job's fixed status codes and database/storage access. After restoring access,
execute the job again. Leases expire after 15 minutes if a process dies. Work remains queued after
failed enumeration or deletion. The queue owns retry timing; Cloud Run task retries are disabled
so an immediate empty retry cannot hide a failed execution. Do not clear pending rows to silence an alert. Never log customer
object names, file names, or content while investigating; use restricted operational records when
individual references are necessary.

Cleanup removes live objects. Provider soft-delete, version history, and disaster-recovery retention
follow the storage provider's configured retention policy. Historical objects whose accounts were
removed before V40 require a separate read-only inventory and reviewed remediation; this job does
not infer deletion authority from a missing account alone.
