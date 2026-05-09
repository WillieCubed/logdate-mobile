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
- gate the production path behind a required-reviewer GitHub Environment
  (`android-production`)
- stay disabled until maintainers explicitly enable each path

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

The production-track job declares `environment: android-production`. Create
that environment under **Settings → Environments → New environment**, then
add the Android leads as required reviewers. Without reviewers configured,
an `android-v*` tag push will queue the production job indefinitely waiting
for approval — which is the desired behavior, not a bug.

## Required Secrets

| Purpose | Secret name | Required for | Notes |
| --- | --- | --- | --- |
| Play Developer API credentials | `ANDROID_PUBLISHER_CREDENTIALS` | Internal + production | Raw service-account JSON content. |
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

Setup splits across two scripts:

```bash
./scripts/sync-firebase-configs.sh android-all       # uploads both Firebase JSONs
./scripts/setup-play-publishing-secrets.sh           # uploads Play API creds + keystore
```

`sync-firebase-configs.sh` validates each JSON before transmission (`jq`
sanity-checks `.project_info.project_id`) and is idempotent — re-running
with a newer file rotates the secret in place.

`setup-play-publishing-secrets.sh`:

- validates that `gh` is installed and authenticated
- helps locate the Play service-account JSON and release keystore
- prompts for the keystore password, key alias, and key password
- uploads the matching GitHub secrets
- can optionally set the internal and production enable variables

If you want either publish path to stay disabled until later, leave its
prompt at `n`.

Production enablement assumes the tagged commit has already been published
to the internal track, because production releases now **promote** that
tested internal release instead of rebuilding the app bundle from source.

## Versioning Model

Automated Play publishing derives app versioning from git history via:

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
2. `versionCode` is commit-stable and does **not** depend on whether a
   release tag exists yet for that commit.
3. The high-significance digits come from the reachable commit count.
4. The low-significance three digits are a deterministic fragment derived
   from the short commit SHA.
5. Internal builds from non-tag refs use a derived `versionName` like
   `1.4.2-main.3+abc1234`.
6. Tag builds resolve a tag-flavored `versionName`, but production promotion
   uses the already-tested internal artifact identified by the same
   commit-stable `versionCode`.
7. The first-ever Play upload must still be done manually in Play Console
   before Gradle Play Publisher can publish subsequent releases.

Because `versionCode` is anchored to the commit graph, the same commit
resolves to the same Play version code on `main`, manual dispatch, and a
later `android-v*` tag. This is what makes promotion-from-internal possible.

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

  - `LOGDATE_PLAY_TRACK=internal`
  - `LOGDATE_VERSION_CODE=<derived>`
  - `LOGDATE_VERSION_NAME=<derived>`
  - `--release-name "<internal release name with short SHA>"`

- Uploads the release bundle (`.aab`) as a workflow artifact
  (`android-release-bundle-internal-<sha>`, 7-day retention) for QA pickup.

### Production track

- Triggers only on `android-v<major>.<minor>.<patch>` tag pushes
- Requires `LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED=true`
- Requires `android-production` Environment approval before any work begins
- Materializes the **release** Firebase config
- Promotes the already-tested internal release with:

  ```bash
  ./gradlew :app:android-main:promoteReleaseArtifact \
    --from-track internal \
    --promote-track production \
    --version-code <derived> \
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
exact internal artifact already tested for the same commit/versionCode.

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
