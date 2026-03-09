# ATProto Library

`studio.hypertext.atproto` is the standalone AT Protocol library surface that LogDate now builds on. The server remains the deployment target in this repository, but protocol contracts and core runtime behavior should live in the shared modules first and be consumed by `server`, not recreated there.

## Module Map

- `shared/atproto-syntax`
  - typed identifiers and parsers for DIDs, handles, NSIDs, record keys, TIDs, and AT URIs
- `shared/atproto-identity`
  - AT Protocol DID rules, DID documents, handle resolution, `did:web`, and `did:plc`
- `shared/atproto-crypto`
  - signing, JWT/JWK helpers, and protocol-facing crypto utilities
- `shared/atproto-plc`
  - PLC directory models and client/runtime behavior
- `shared/atproto-repo`
  - repo records, deterministic block storage, commits, export/import, and the canonical repo engine
- `shared/atproto-xrpc`
  - Ktor-backed XRPC client primitives
- `shared/atproto-lexicon`
  - lexicon parsing, validation, registry lookups, and deterministic codegen output
- `shared/atproto-pds`
  - shared request/response models and service contracts for discovery, identity, OAuth, and repo surfaces

## Maven Coordinates

- `studio.hypertext.atproto:atproto-crypto:$version`
- `studio.hypertext.atproto:atproto-syntax:$version`
- `studio.hypertext.atproto:atproto-identity:$version`
- `studio.hypertext.atproto:atproto-xrpc:$version`
- `studio.hypertext.atproto:atproto-repo:$version`
- `studio.hypertext.atproto:atproto-plc:$version`
- `studio.hypertext.atproto:atproto-lexicon:$version`
- `studio.hypertext.atproto:atproto-pds:$version`

The repo-wide default version lives in [`gradle.properties`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/gradle.properties) as `atproto.version`.

## Quick Start

```kotlin
val repo = AtprotoDid.require("did:plc:ewvi7nxzyoun6zhxrhs64oiz")
val collection = Nsid.require("studio.hypertext.logdate.entry")
val engine = DefaultRepoEngine(InMemoryRepoBlockStore())

engine.createRecord(
    repo = repo,
    collection = collection,
    value = buildJsonObject {
        put("\$type", collection.toString())
        put("text", "hello")
    },
    recordKey = RecordKey.require("entry-1"),
)
```

```kotlin
val client = KtorXrpcClient(
    httpClient = HttpClient(),
    baseUrl = "https://example.com",
)

val response: ResolveHandleResponse =
    client.query(Nsid.require("com.atproto.identity.resolveHandle")) {
        queryParameter("handle", "alice.example.com")
    }
```

## Publishing

Publish locally:

```bash
./gradlew \
  :shared:atproto-crypto:publishToMavenLocal \
  :shared:atproto-syntax:publishToMavenLocal \
  :shared:atproto-identity:publishToMavenLocal \
  :shared:atproto-xrpc:publishToMavenLocal \
  :shared:atproto-repo:publishToMavenLocal \
  :shared:atproto-plc:publishToMavenLocal \
  :shared:atproto-lexicon:publishToMavenLocal \
  :shared:atproto-pds:publishToMavenLocal
```

Publish to a remote Maven repository by setting:

- `ATPROTO_PUBLISH_URL`
- `ATPROTO_PUBLISH_USERNAME`
- `ATPROTO_PUBLISH_PASSWORD`
- `SIGNING_KEY_ID` optional
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

The build logic uses those values for Maven repository credentials, POM metadata, Dokka-backed `javadoc` jars, and in-memory PGP signing.

## Standalone Sample

The consumer sample in [`samples/atproto-consumer`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/samples/atproto-consumer) is intentionally outside the main Gradle build. It depends on `mavenLocal()` artifacts, not project modules, so it verifies the published API surface the way an external JVM consumer would.

Run it after publishing the ATProto modules locally:

```bash
./gradlew -p samples/atproto-consumer run
```

## Current Scope

- Standalone Kotlin/KMP modules with explicit public APIs and typed value objects
- ATProto identity resolution, OAuth/PDS wire models, repo CRUD plus commit/export primitives, and low-level XRPC client support
- Server integration in this repo now consumes shared PDS and repo contracts instead of defining duplicate route DTOs
- Publishable Maven metadata, signing hooks, Dokka-backed documentation jars, and a standalone consumer sample

## Not Yet Complete

- Full CAR/MST interoperability with external AT Protocol implementations
- Lexicon code generation for the full protocol surface
- A standalone PDS server runtime module
- Hosted release automation

Until those pieces land, treat the current library as a strong standalone core rather than a full end-to-end AT Protocol SDK.
