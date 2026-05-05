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
  - lexicon parsing, validation, registry lookups, official `com.atproto.*` resources, and deterministic codegen output
- `shared/atproto-pds`
  - shared request/response models and service contracts for discovery, identity, OAuth, and repo surfaces
- `shared/atproto-pds-runtime`
  - reusable runtime implementations for discovery and repo services backed by the shared PDS contracts

## Maven Coordinates

The recommended way to consume the library is via the BOM, which pins every
module to a single coordinated release version:

```kotlin
dependencies {
    implementation(platform("studio.hypertext.atproto:atproto-bom:0.1.0"))

    // Pick the modules you need — versions are inferred from the BOM.
    implementation("studio.hypertext.atproto:atproto-syntax")
    implementation("studio.hypertext.atproto:atproto-identity")
    implementation("studio.hypertext.atproto:atproto-repo")
    implementation("studio.hypertext.atproto:atproto-xrpc")
}
```

The full module set:

- `studio.hypertext.atproto:atproto-bom:$version` (BOM — pin once, version-less elsewhere)
- `studio.hypertext.atproto:atproto-crypto:$version`
- `studio.hypertext.atproto:atproto-syntax:$version`
- `studio.hypertext.atproto:atproto-identity:$version`
- `studio.hypertext.atproto:atproto-xrpc:$version`
- `studio.hypertext.atproto:atproto-repo:$version`
- `studio.hypertext.atproto:atproto-plc:$version`
- `studio.hypertext.atproto:atproto-lexicon:$version`
- `studio.hypertext.atproto:atproto-pds:$version`
- `studio.hypertext.atproto:atproto-pds-runtime:$version`

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

Maintainers should use the detailed publishing guide in
[`atproto-publishing.md`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/docs/reference/atproto-publishing.md)
for property precedence, remote publish task names, signing behavior, and
artifact expectations.

Publish locally:

```bash
./gradlew publishAtprotoToMavenLocal
```

Publish to a remote Maven repository by setting:

- `ATPROTO_PUBLISH_URL`
- `ATPROTO_PUBLISH_USERNAME`
- `ATPROTO_PUBLISH_PASSWORD`
- `SIGNING_KEY_ID` optional
- `SIGNING_KEY`
- `SIGNING_PASSWORD`

The build logic uses those values for Maven repository credentials, POM metadata, Dokka-backed `javadoc` jars, and in-memory PGP signing.

Generate API docs for every ATProto module:

```bash
./gradlew generateAtprotoDokka
```

Regenerate the checked-in LogDate lexicon models:

```bash
./gradlew :shared:atproto-lexicon:generateLogDateLexicons
```

Regenerate the checked-in official AT Protocol lexicon models used by this repo:

```bash
./gradlew :shared:atproto-lexicon:generateOfficialAtprotoLexicons
```

Android Studio users can run the same Dokka task through
[`Generate ATProto Dokka.run.xml`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/.run/Generate%20ATProto%20Dokka.run.xml).

## Standalone Sample

The consumer sample in [`samples/atproto-consumer`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/samples/atproto-consumer) is intentionally outside the main Gradle build. It depends on `mavenLocal()` artifacts, not project modules, so it verifies the published API surface the way an external JVM consumer would.

Run it after publishing the ATProto modules locally:

```bash
./gradlew -p samples/atproto-consumer run
```

## Current Scope

- Standalone Kotlin/KMP modules with explicit public APIs and typed value objects
- ATProto identity resolution, OAuth/PDS wire models, reusable PDS runtime services, repo CRUD plus commit/export primitives, and low-level XRPC client support
- Server integration in this repo now consumes shared PDS and repo contracts instead of defining duplicate route DTOs
- Checked-in LogDate lexicon JSON documents plus deterministic generated Kotlin models
- Checked-in official `com.atproto.*` lexicon JSON documents plus deterministic generated Kotlin models for the current server/repo/identity surface
- Publishable Maven metadata, signing hooks, Dokka-backed documentation jars, aggregate Dokka/publish tasks, and a standalone consumer sample
- GitHub Actions release automation for hosted Maven publishing

## Not Yet Complete

- Lexicon code generation for the full protocol surface
- A durable standalone PDS deployment story outside this repository's `server` module

The current library is a strong standalone core for syntax, identity, repo, PDS contracts/runtime,
and hosted-PDS server consumption. The remaining work is broader protocol surface and packaging,
not the core repo/CAR/MST implementation.
