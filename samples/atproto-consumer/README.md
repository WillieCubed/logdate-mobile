# ATProto Consumer Sample

This sample is intentionally outside the main Gradle build. It consumes
`studio.hypertext.atproto` from `mavenLocal()` so it exercises the
published library surface instead of project dependencies.

## Run

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

./gradlew -p samples/atproto-consumer run
```
