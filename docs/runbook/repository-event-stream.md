# Turn on the repository event stream

This runbook switches on the stream that tells other AT Protocol apps what accounts publish from
LogDate. Do it after identities are published, because the server shows only accounts whose DID other
apps can look up. [Repository event stream](../reference/repository-event-stream.md) describes what
the stream does and which records it shows.

The stream can be turned on in staging, but staging never publishes `did:plc` identities, so no
staging account is shown there; use staging to check that the server starts and the routes answer.
Relays are named only in production.

## Before you start

Check these, in this order:

1. The identity publishing command has published the production identities. Follow the
   [identity publishing runbook](identity-publishing.md) and confirm its `status` command lists
   every account as `published`. Until then no account is shown, and turning the stream on does
   nothing visible.
2. Accounts keep their journals in personal spaces. Only those accounts are shown. Accounts still on
   legacy sync stay private until their journals move; see the [journal moves runbook](journal-moves.md).
3. The database migrations are applied. The stream needs migration V51, which creates the event log.
   Deploying a release applies it, and the stream does nothing until the flag is on.

## Turn it on

1. In the production deployment contract, set `ATPROTO_FIREHOSE_ENABLED` to `"true"` in `env_vars`
   and leave `ATPROTO_RELAY_HOSTS` out. Deploy.
2. Check the stream from outside. Replace `HOST` with the server's hostname:

   ```bash
   curl -s https://HOST/xrpc/com.atproto.sync.listRepos
   ```

   It lists the shown repositories, and an account you know keeps its journal in a personal space
   appears in it. Then read one public record without signing in:

   ```bash
   curl -s "https://HOST/xrpc/com.atproto.repo.getRecord?repo=DID&collection=app.logdate.actor.profile&rkey=self"
   ```

3. Check that a private collection stays closed. This must answer `RecordNotFound`:

   ```bash
   curl -s "https://HOST/xrpc/com.atproto.repo.getRecord?repo=DID&collection=app.logdate.journal.entry&rkey=anything"
   ```

4. Tell the relays. Set `ATPROTO_PLC_PUBLISH_ENABLED` to `"true"` if it is not already, because a
   relay checks every account against its published identity and the contract renderer refuses
   relay hosts without it. Set `ATPROTO_RELAY_HOSTS` to the relay hostnames, for example
   `relay1.us-east.bsky.network`, and deploy again. At startup the server asks each relay to crawl it.
   Look for `SERVER_RELAY_CRAWL_REQUESTED` in the logs, one for each relay. `SERVER_RELAY_CRAWL_REFUSED`
   means a relay answered with an error, and `SERVER_RELAY_CRAWL_FAILED` means it could not be reached.

## Turn it off

Remove `ATPROTO_FIREHOSE_ENABLED` from `env_vars`, or set it to `"false"`, and deploy. The stream
route disappears, public reads close, and no new events are written. Relays keep what they already
received, so turning the stream off does not take published records back from them. The event log
stays in the database, and the server deletes events older than seven days the next time the stream
runs.
