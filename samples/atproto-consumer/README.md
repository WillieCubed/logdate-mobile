# ATProto Consumer Sample

This sample is intentionally outside the main Gradle build. It consumes
`studio.hypertext.atproto` from `mavenLocal()` so it exercises the
published library surface instead of project dependencies.

It is the quickest way to prove that the ATProto modules can be published and
then consumed by an external JVM project without any dependency on `server` or
the main repo module graph.

## Run

```bash
./gradlew publishAtprotoToMavenLocal

./gradlew -p samples/atproto-consumer run
```

To regenerate the ATProto API docs before validating the sample, run:

```bash
./gradlew generateAtprotoDokka
```

Expected output:

```text
Resolved alice.example.com to did:web:example.com and loaded at://did:web:example.com/studio.hypertext.logdate.entry/entry-1
```

## Version Override

By default the sample reads `atproto.version` from the root
[`gradle.properties`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/gradle.properties).

To point it at a different locally published version:

```bash
./gradlew -p samples/atproto-consumer run -PatprotoVersion=0.1.0
```
