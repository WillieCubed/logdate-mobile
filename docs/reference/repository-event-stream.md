# Repository event stream

Other AT Protocol apps learn what a person published from LogDate in two ways. They ask the server
for it, or a relay tells them. A relay is a service that follows many servers and passes what they
announce to every app that listens. This page describes what the LogDate server announces, which
parts of an account it shows to strangers, and exactly how it behaves, so you can build against it
or check that it keeps its promises.

The stream is off unless a deployment sets `ATPROTO_FIREHOSE_ENABLED` to `true`; see the
[environment variables reference](../../server/docs/environment-variables.md). To switch it on in
production, follow the [repository event stream runbook](../runbook/repository-event-stream.md). The
reasons for splitting each account into a public repository and private spaces are in
[Private and shared storage](../superpowers/specs/2026-10-18-private-and-shared-storage-design.md).

## What is public

An account has one repository of records that anyone may read, and any number of private spaces that
no one but the owner can. Only four kinds of record are public:

| Collection | What it holds |
|---|---|
| `app.logdate.actor.profile` | The person's name, description and photo. |
| `app.logdate.crypto.keyDeclaration` | A public key a device publishes so others can share with it. |
| `site.standard.publication` | A publication the person publishes stories under. |
| `site.standard.document` | A story the person chose to publish. |

Collections that other apps are allowed to write through OAuth are public as well, because an app
that wrote a record expects other apps to read it. None are open yet.

Everything else is private and is never shown or announced: the `studio.hypertext.logdate.*` records
that older sync used, the `app.logdate.space.*` spaces, every sealed record, and every other
`app.logdate.*` collection, including ones added later. A collection is public only by being named in
`PublicRepoPolicy`, so a new collection starts private.

## Which accounts are shown

The server shows an account's repository only when all of these hold:

- the account is active;
- it keeps its journal in its personal space, not in legacy sync, because a legacy account stores
  every entry, journal and draft as a record in the same repository;
- other apps can look up its DID, which means a `did:web`, or a `did:plc` that
  `ATPROTO_PLC_PUBLISH_ENABLED` has published to the PLC directory.

For any other account every method below answers as it did before the stream existed: it asks the
caller to sign in, and it never confirms that the account exists.

Showing individual records is not enough to show a whole repository. A repository exports as a tree
that names the collection and key of every record it holds, so a tree with a single private record in
it would reveal that record's name. The server therefore exports and announces a repository only while
every record in its tree is in a public collection. If a private record gets in, the repository goes
quiet instead of leaking, and the server refuses to write journal records into the repository of an
account that keeps its journal in a personal space, so that cannot happen by accident.

## Methods

None of these needs a sign-in. Each answers for a shown repository only.

| Method | What it returns |
|---|---|
| `com.atproto.sync.subscribeRepos` | The event stream, over a WebSocket. |
| `com.atproto.sync.listRepos` | The shown repositories, 500 per page by default and 1000 at most, ordered by DID. |
| `com.atproto.sync.getRepo` | The repository as a CAR file. `since` limits it to newer blocks. |
| `com.atproto.sync.getLatestCommit` | The commit CID and revision. |
| `com.atproto.sync.getRepoStatus` | Whether the repository is active, and its revision. |
| `com.atproto.repo.getRecord` | One record of a public collection. |
| `com.atproto.repo.listRecords` | A page of a public collection. A private collection is empty. |
| `com.atproto.sync.getBlob` | A blob that a public record refers to. |

A record in a private collection answers `RecordNotFound`, exactly as a record that does not exist
does. A blob that no public record refers to answers `BlobNotFound`, even if the account uploaded it.

## Events

Each frame on the WebSocket is a DAG-CBOR header followed by a DAG-CBOR body. Every event carries a
`seq`: the server numbers events 1, 2, 3 and so on, and a relay that remembers the last number it saw
can ask for everything after it.

| Event | When the server sends it |
|---|---|
| `#commit` | A shown repository changed. It lists the records that were created, updated or deleted, and carries the commit, the record tree and the new records as a CAR file, so a relay can check the change without asking for more. |
| `#sync` | A relay must fetch the repository again, because a diff on top of a state it never saw would not verify. Sent when an account is first announced, when the commit before this one was never announced, and when a commit changes more than 200 records or its blocks exceed 2 MB. |
| `#identity` | An account got an identity, or its handle or signing key changed. |
| `#account` | An account is active, or was deleted. |

A `#commit` names the revision it follows in `since` and the tree root that revision had in
`prevData`. The first commit of a repository has neither.

## Following the stream

Connect to `/xrpc/com.atproto.sync.subscribeRepos` and optionally pass `cursor`:

- With no cursor the stream starts at the present.
- With a cursor, the server first replays every event after that number, then keeps the connection
  open and sends new events as they happen.
- A cursor older than the oldest event the server still keeps gets an `#info` message named
  `OutdatedCursor`, followed by everything the server has. The server keeps events for seven days.
- A cursor beyond the newest event gets a `FutureCursor` error frame and the connection closes.
- A follower that stops reading gets a `ConsumerTooSlow` error frame and the connection closes.
- The server serves 64 followers at a time and closes extra connections with `TRY_AGAIN_LATER`.

Events live in the database, so every server instance serves the same log. An instance finds events
that another one stored by looking every half second.

The library that reads these frames is `RepoEventFrames` in `shared/atproto-repo`. It turns a frame
into a `RepoCommitEvent`, `RepoSyncEvent`, `RepoIdentityEvent`, `RepoAccountEvent`, `RepoInfoMessage`
or `RepoErrorMessage`, and `CarCodec.readCar` reads the blocks a commit carries.

## Asking relays to follow

A relay has to be told a server exists. With `ATPROTO_RELAY_HOSTS` set, the server sends each relay
`com.atproto.sync.requestCrawl` with its own hostname when it starts, and again when it announces an
account for the first time, at most once every ten minutes. A relay that cannot be reached at startup
is asked again after 10, 20, 40 and 80 seconds.

## Log codes

Production logs keep only approved messages. These are the codes the stream writes.

| Code | Meaning |
|---|---|
| `SERVER_REPO_EVENT_NOT_RECORDED` | Announcing a change failed. The write itself succeeded, and the next change announces the repository's state. |
| `SERVER_REPO_EVENT_COMMIT_UNREADABLE` | A commit could not be compared with the one before it, so the server sent `#sync` instead. |
| `SERVER_REPO_EVENT_PRIVATE_RECORDS_HELD_BACK` | A repository holding a private record was not announced. |
| `SERVER_REPO_EVENT_FOLLOWER_FAILED` | A follower's connection stopped after an error. |
| `SERVER_REPO_EVENT_PRUNE_FAILED` | Deleting events older than seven days failed. The next hourly sweep retries it. |
| `SERVER_RELAY_CRAWL_REQUESTED` | A relay accepted a crawl request. |
| `SERVER_RELAY_CRAWL_REFUSED` | A relay answered the request with an error. |
| `SERVER_RELAY_CRAWL_FAILED` | A relay could not be reached. |
