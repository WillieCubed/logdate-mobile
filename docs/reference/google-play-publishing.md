# Google Play Publishing

This document covers the Android app's automated Google Play publishing path.
It explains the repo-level inputs, GitHub Actions workflow, versioning model,
and the helper scripts used to prepare the required secrets.

## Scope

The automated path is intentionally narrow:

- publish the Android app bundle (`.aab`) only
- publish **internal** track builds on every push to `main`
- allow **manual** internal publishes via `workflow_dispatch`
- promote **production** releases only from `android-v*` tag pushes
- read the publishing secrets from the `production` GitHub Environment, which
  only `main` and release tags may use
- stay disabled until maintainers explicitly enable each path

`./run setup production` sets all of this up and verifies it; see
[`project-setup.md`](project-setup.md).

### Dogfood and production

"Dogfood" is the Play **internal testing** track. Every push to `main`
publishes a real, non-debuggable `release` bundle there, built against the
production backend `https://cloud.logdate.app`. The local `dogfood` Gradle
build type is something else: a debuggable build for sideloading, which Play
would reject.

Production is the same bundle, promoted from internal testing by an
`android-v*` tag. Nothing is rebuilt for production.

The workflow lives in [`publish-android-play.yml`](../../.github/workflows/publish-android-play.yml).

The Android module wiring lives in [`app/android-main/build.gradle.kts`](../../app/android-main/build.gradle.kts)
and uses Gradle Play Publisher.

## Safety Switch

Automated publishing is guarded by repository variables:

- `LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED`
- `LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED`

If a variable is not set to `true`, that publish path is skipped. This keeps
`main` shippable before the Play and signing secrets exist and lets internal
and production roll out independently.

## Required GitHub Environment

Both jobs declare `environment: production`. Its deployment policy, not a
required reviewer, is what protects the secrets: only `main`, `android-v*`
tags, and `server-v*` tags may use it. A reviewer gate would also pause every
internal publish from `main`, which defeats continuous dogfooding. The
`gh-environment` setup step enforces this policy.

## Required Secrets

| Purpose | Secret name | Required for | Notes |
| --- | --- | --- | --- |
| Play Developer API access | none (variable `LOGDATE_PLAY_SERVICE_ACCOUNT`) | Internal + production | Keyless. Both jobs authenticate through Workload Identity Federation as this service account, and Gradle Play Publisher uses the resulting application-default credentials. |
| Firebase debug config | `LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_DEBUG_BASE64` | CI, screenshot tests, local debug parity | Base64 of `app/android-main/google-services.json`. Materialized by [`setup-firebase-configs`](../../.github/actions/setup-firebase-configs/action.yml) with `android-flavor: debug` (default). |
| Firebase release config | `LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_RELEASE_BASE64` | Internal + production Play publishing | Base64 of `app/android-main/src/release/google-services.json`. Materialized by [`setup-firebase-configs`](../../.github/actions/setup-firebase-configs/action.yml) with `android-flavor: release`. |
| Release keystore file | `LOGDATE_RELEASE_STORE_BASE64` | Internal | Base64-encoded `.jks` or `.keystore` file content. |
| Release keystore password | `LOGDATE_RELEASE_STORE_PASSWORD` | Internal | Passed into the existing Android release signing config. |
| Release key alias | `LOGDATE_RELEASE_KEY_ALIAS` | Internal | Passed into the existing Android release signing config. |
| Release key password | `LOGDATE_RELEASE_KEY_PASSWORD` | Internal | Passed into the existing Android release signing config. |

The workflow materializes the keystore at runtime and removes it before the
job exits. Firebase config materialization is handled by the
`setup-firebase-configs` composite action; see
[`docs/runbook/release-secrets.md`](../runbook/release-secrets.md) for the
full secret rotation runbook.

## Helper Scripts

`./run setup production` calls these for you. They remain usable on their own:

```bash
./scripts/create-signing-keystore.sh --environment production   # creates the keystore
./scripts/sync-firebase-configs.sh android-all       # uploads both Firebase JSONs
./scripts/upload-play-keystore.sh --keyless # uploads the upload keystore
```

`create-signing-keystore.sh` owns the keystore itself. It creates one per
environment on first run and reuses it forever after — re-running is a no-op
that reprints the same values, and it refuses to continue if the keystore
survives but its credentials file does not, because that passphrase cannot be
recovered. Every downstream value is derived from the certificate rather than
transcribed: the colon-hex fingerprint for Digital Asset Links, the base64url
`apk-key-hash` origin for WebAuthn, the `android_signing_certificates` block
for Terraform, and the `LOGDATE_RELEASE_*` secrets below. Pass
`--play-fingerprint` once the app exists in Play Console to fold the Play
app-signing certificate in, and `--write-assetlinks PATH` to write the
`assetlinks.json` served from `logdate.app` directly.

`sync-firebase-configs.sh` validates each JSON before transmission (`jq`
sanity-checks `.project_info.project_id`) and is idempotent — re-running
with a newer file rotates the secret in place.

`upload-play-keystore.sh`:

- validates that `gh` is installed and authenticated
- reads the keystore settings from `~/.logdate-signing/production-upload.env`,
  prompting only for what is missing (`--non-interactive` never prompts)
- uploads the keystore secrets to the `production` environment on stdin, so no
  value ever appears in a command line
- with `--keyless` (what setup uses), skips `ANDROID_PUBLISHER_CREDENTIALS`,
  because CI authenticates to Play through Workload Identity Federation
- can optionally set the internal and production enable variables

If you want either publish path to stay disabled until later, leave its
prompt at `n`.

Production enablement assumes the tagged commit has already been published
to the internal track, because production releases now **promote** that
tested internal release instead of rebuilding the app bundle from source.

## Versioning Model

### versionCode: assigned by Play

Play rejects any upload whose `versionCode` is not higher than every code it
has seen for the app. Rather than derive codes from git, which breaks when
history is rewritten, squashed, or fetched shallowly, Gradle Play Publisher
runs with `resolutionStrategy = AUTO`. Every upload gets Play's highest
existing `versionCode` + 1, so codes can never collide or go backwards.

A commit therefore has no predictable `versionCode`. To promote a tag,
`scripts/resolve-play-internal-release.sh` looks up the internal release whose
name contains the tag's short SHA, and the workflow promotes that release's
code. When the tagged commit never reached the internal track, the lookup fails
and nothing is promoted.

### versionName: derived from tags

The version name comes from git history via:

```bash
./scripts/resolve-android-play-version.sh
```

The script expects Android release tags in this format:

- `android-v<major>.<minor>.<patch>`

Examples:

- `android-v0.1.0`
- `android-v1.4.2`

Rules:

1. The latest reachable `android-v*` tag defines the base version.
2. Internal builds from non-tag refs use a derived `versionName` like
   `1.4.2-main.3+abc1234`.
3. Tag builds resolve a tag-flavored `versionName`.
4. Internal release names include the short SHA, which is how promotion finds
   the build for a tag.
5. The first-ever Play upload must still be done manually in Play Console
   before Gradle Play Publisher can publish subsequent releases.

## Workflow Behavior

The workflow has two publish paths:

### Internal track

- Triggers on every push to `main`
- Also supports manual `workflow_dispatch`
- Requires `LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED=true`
- Materializes the **release** Firebase config because Gradle Play Publisher
  builds the signed `release` bundle even when publishing to the internal
  track
- Publishes with:

  ```bash
  ./gradlew :app:android-main:publishReleaseBundle
  ```

  using:

  - `-Plogdate.backendUrl=https://cloud.logdate.app`, explicitly, so a change
    to `gradle.properties` can never retarget Play builds
  - `LOGDATE_PLAY_TRACK=internal`
  - `LOGDATE_VERSION_NAME=<derived>` (Play assigns the `versionCode`)
  - `--release-name "<internal release name with short SHA>"`

- Uploads the release bundle (`.aab`) as a workflow artifact
  (`android-release-bundle-internal-<sha>`, 7-day retention) for QA pickup.

### Production track

- Triggers only on `android-v<major>.<minor>.<patch>` tag pushes
- Requires `LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED=true`
- Runs in the `production` Environment, which only release tags and `main` may use
- Materializes the **release** Firebase config
- Promotes the already-tested internal release with:

  ```bash
  ./gradlew :app:android-main:promoteReleaseArtifact \
    --from-track internal \
    --promote-track production \
    --version-code <from resolve-play-internal-release.sh> \
    --release-name "<tag release name with short SHA>" \
    --release-status completed
  ```

- After successful promotion, creates a draft GitHub Release tied to the
  triggering tag with auto-generated notes from commit history. The matching
  `.aab` lives in the internal-track workflow artifact (7-day retention);
  attach it to the release manually if you want it persisted longer.

The production path is intentionally lighter than the internal one:

- it does **not** rebuild the app bundle
- it does **not** require the keystore

That optimization is safe only because the production job promotes the
exact internal artifact already tested for the tagged commit.

## Local Verification

To verify the Android release bundle still builds locally without
publishing:

```bash
./gradlew :app:android-main:bundleRelease \
  -Plogdate.versionCode=1010000 \
  -Plogdate.versionName=1.1.0-local
```

To inspect the Gradle Play Publisher task surface:

```bash
./gradlew :app:android-main:help --task publishReleaseBundle
./gradlew :app:android-main:help --task promoteReleaseArtifact
```

Local publishing is possible too, but it requires the same Play
service-account credentials and release signing inputs as CI.
