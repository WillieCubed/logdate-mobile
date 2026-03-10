# ATProto Publishing

This document is for maintainers of the standalone `studio.hypertext.atproto`
modules in this repository. It explains the shared publication convention,
which inputs it reads, what it publishes, and how to run local and remote
publish flows without guessing.

## Scope

The publishing convention is implemented in
[`AtprotoPublishedModulePlugin.kt`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/build-logic/src/main/kotlin/app/logdate/AtprotoPublishedModulePlugin.kt)
and applied by every `shared/atproto-*` module.

Today it is intentionally ATProto-specific, not a generic repo-wide publishing
plugin.

The repo also includes supporting release workflow and aggregate tasks:

- [`publish-atproto.yml`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/.github/workflows/publish-atproto.yml)
  runs hosted Maven publication from CI
- `publishAtprotoToMavenLocal` publishes the full module set locally
- `generateAtprotoDokka` generates HTML docs for the full module set

## Plugin ID

Apply this convention with:

```kotlin
plugins {
    id("app.logdate.atproto-published-module")
}
```

Each module still owns its own Kotlin target setup, dependencies, namespace,
and `description`. The convention only handles publication concerns.

## What The Plugin Does

When applied, the plugin:

- applies `org.jetbrains.dokka`
- applies `maven-publish`
- applies `signing`
- sets the default group to `studio.hypertext.atproto`
- sets the default version to `0.1.0`
- registers `dokkaHtmlJar`
- attaches that jar to every Maven publication as the `javadoc` artifact
- attaches shared POM metadata to every publication
- optionally registers a remote Maven repository named `atproto`
- optionally signs all publications with in-memory PGP keys

This is meant to keep all `shared/atproto-*` modules aligned on one release
policy and one POM shape.

## Property Precedence

For every configurable value, precedence is:

1. Gradle property
2. environment variable
3. hard-coded default, when one exists

Supported overrides:

| Purpose | Gradle Property | Environment Variable | Default |
| --- | --- | --- | --- |
| Group | `atproto.group` | `ATPROTO_GROUP` | `studio.hypertext.atproto` |
| Version | `atproto.version` | `ATPROTO_VERSION` | `0.1.0` |
| Repository URL | `atproto.publish.url` | `ATPROTO_PUBLISH_URL` | none |
| Repository username | `atproto.publish.username` | `ATPROTO_PUBLISH_USERNAME` | none |
| Repository password | `atproto.publish.password` | `ATPROTO_PUBLISH_PASSWORD` | none |
| Signing key ID | `signingKeyId` | `SIGNING_KEY_ID` | optional |
| Signing key | `signingKey` | `SIGNING_KEY` | none |
| Signing password | `signingPassword` | `SIGNING_PASSWORD` | none |

If no repository URL is configured, the plugin does not register a remote
publish repository at all.

If no signing key and signing password are configured, the plugin does not
enable signing. This is intentional so local development can publish unsigned
artifacts to `mavenLocal()` with zero release credentials.

## Published Artifacts

Each ATProto module publishes Kotlin Multiplatform outputs, which means Gradle
creates multiple publications per module.

For a representative module such as `shared/atproto-syntax`, the generated
publish task surface includes:

- `publishToMavenLocal`
- `publishAllPublicationsToAtprotoRepository`
- `publishKotlinMultiplatformPublicationToAtprotoRepository`
- `publishJvmPublicationToAtprotoRepository`
- `publishAndroidPublicationToAtprotoRepository`
- `publishIosArm64PublicationToAtprotoRepository`
- `publishIosSimulatorArm64PublicationToAtprotoRepository`

The plugin also ensures the module exposes:

- `sourcesJar`
- `dokkaHtmlJar`

The root multiplatform artifact published to Maven Local includes:

- the main module jar
- `-sources.jar`
- `-javadoc.jar`
- `.pom`
- `.module`
- Kotlin tooling metadata

## Shared POM Metadata

The convention writes the same POM metadata to every ATProto module:

- project name from the Gradle project name
- project description from the module `description`
- repository URL pointing at the main repo
- Apache 2.0 license
- organization metadata for The Hypertext Studio
- developer metadata for The Hypertext Studio
- SCM URLs
- GitHub issue tracker URL

If you change ownership, licensing, or repository location, update the
convention plugin instead of patching individual modules.

## Local Publish Flow

Publish the current ATProto module set to Maven Local:

```bash
./gradlew publishAtprotoToMavenLocal
```

This is the correct flow for:

- validating POM metadata locally
- checking `sources` and `javadoc` artifacts
- exercising the standalone sample consumer
- preparing local integration tests outside the main repo build

Generate all ATProto Dokka HTML publications:

```bash
./gradlew generateAtprotoDokka
```

This task writes real HTML output under each module's `build/dokka/html/`
directory, for example:

- `shared/atproto-syntax/build/dokka/html/index.html`
- `shared/atproto-pds-runtime/build/dokka/html/index.html`

The repo includes an Android Studio run configuration for the same task in
[`Generate ATProto Dokka.run.xml`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/.run/Generate%20ATProto%20Dokka.run.xml).

Regenerate the checked-in LogDate lexicon models:

```bash
./gradlew :shared:atproto-lexicon:generateLogDateLexicons
```

Regenerate the checked-in official `com.atproto.*` lexicon models used by the
current server surface:

```bash
./gradlew :shared:atproto-lexicon:generateOfficialAtprotoLexicons
```

## Remote Publish Flow

Set the repository and signing inputs, then publish from the module you want.

Example:

```bash
ATPROTO_PUBLISH_URL=<repository-url> \
ATPROTO_PUBLISH_USERNAME=<repository-username> \
ATPROTO_PUBLISH_PASSWORD=<repository-password> \
SIGNING_KEY=<armored-signing-key> \
SIGNING_PASSWORD=<signing-passphrase> \
  ./gradlew :shared:atproto-syntax:publishAllPublicationsToAtprotoRepository
```

You can replace `publishAllPublicationsToAtprotoRepository` with a narrower
publication task if you only need one target variant.

For the full hosted release flow, the repo also ships
[`publish-atproto.yml`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/.github/workflows/publish-atproto.yml).
It publishes every ATProto module on `atproto-v*` tags or manual dispatch after
validating the Maven repository and signing secrets.

## Standalone Consumer Verification

The standalone sample in
[`samples/atproto-consumer`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/samples/atproto-consumer)
is intentionally outside the main build so it consumes published artifacts,
not project dependencies.

After publishing locally:

```bash
./gradlew -p samples/atproto-consumer run
```

The expected output is a single success line showing a resolved handle and a
loaded AT URI from the sample repo record.

## What This Plugin Does Not Do

The convention does not currently provide:

- staging or promotion logic
- repository-specific validation rules
- release notes generation
- generic non-ATProto publication defaults

CI release automation exists at the repo level through the GitHub Actions
workflow. The plugin intentionally stays narrower and only owns module-level
publication behavior.

Those concerns remain outside the plugin on purpose. This plugin owns the
module-level publication contract, not the entire release pipeline.

## When To Generalize It

If this repo grows more publishable libraries, the likely refactor is:

1. extract a generic publishing convention plugin
2. keep a thin ATProto wrapper that supplies the ATProto-specific defaults

Until then, keeping this plugin ATProto-specific is simpler and makes the
expected artifact family obvious.
