# Google Play Publishing

This document covers the Android app's automated Google Play publishing path.
It explains the repo-level inputs, GitHub Actions workflow, versioning model,
and the helper script used to prepare the required secrets without guessing.

## Scope

The automated path is intentionally narrow:

- publish the Android app bundle (`.aab`) only
- publish **internal** builds on every push to `main`
- allow **manual** internal publishes via `workflow_dispatch`
- publish **production** builds only from `android-v*` tag pushes
- stay disabled until maintainers explicitly enable each path

The workflow lives in
[`publish-android-play.yml`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/.github/workflows/publish-android-play.yml).

The Android module wiring lives in
[`app/android-main/build.gradle.kts`](/Users/williecubed/Projects/TheHypertextStudio/logdate-android/app/android-main/build.gradle.kts)
and uses Gradle Play Publisher.

## Safety Switch

Automated publishing is guarded by repository variables:

- `LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED`
- `LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED`

If a variable is not set to `true`, that publish path is skipped. This keeps
`main` shippable before the Play and signing secrets exist and lets internal and
production roll out independently.

## Required Secrets

| Purpose | Secret name | Notes |
| --- | --- | --- |
| Play Developer API credentials | `ANDROID_PUBLISHER_CREDENTIALS` | Raw service-account JSON content. |
| Firebase/Google Services config | `LOGDATE_ANDROID_GOOGLE_SERVICES_JSON` | Raw `google-services.json` content written into `app/android-main/` during CI. |
| Release keystore file | `LOGDATE_RELEASE_STORE_BASE64` | Base64-encoded `.jks` or `.keystore` file content. |
| Release keystore password | `LOGDATE_RELEASE_STORE_PASSWORD` | Passed into the existing Android release signing config. |
| Release key alias | `LOGDATE_RELEASE_KEY_ALIAS` | Passed into the existing Android release signing config. |
| Release key password | `LOGDATE_RELEASE_KEY_PASSWORD` | Passed into the existing Android release signing config. |

The workflow materializes the keystore and `google-services.json` at runtime and
removes them before the job exits.

## Helper Script

Use the interactive helper to locate the Play JSON, `google-services.json`, and
release keystore, then upload the required GitHub Actions secrets:

```bash
./scripts/setup-play-publishing-secrets.sh
```

The helper:

- validates that `gh` is installed and authenticated
- helps locate the service-account JSON, `google-services.json`, and release keystore
- prompts for the keystore password, key alias, and key password
- uploads the matching GitHub secrets
- can optionally set the internal and production enable variables

If you want either publish path to stay disabled until later, leave its prompt
at `n`.

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
2. `versionCode` is packed from the tag's semantic version plus the commit
   distance from that tag.
3. Internal builds from non-tag refs use a derived `versionName` like `1.4.2-main.3`.
4. More than `999` commits from the same base tag are rejected; cut a new tag.
5. The first-ever Play upload must still be done manually in Play Console before
   Gradle Play Publisher can publish subsequent releases.

Because `versionCode` is derived from the release tag lineage, new `android-v*`
tags must move forward semantically. Do not reuse or move Android release tags.

## Workflow Behavior

The workflow has two publish paths:

### Internal track

- Triggers on every push to `main`
- Also supports manual `workflow_dispatch`
- Requires `LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED=true`
- Publishes with:

  ```bash
  ./gradlew :app:android-main:publishReleaseBundle
  ```

  using:

  - `LOGDATE_PLAY_TRACK=internal`
  - `LOGDATE_VERSION_CODE=<derived>`
  - `LOGDATE_VERSION_NAME=<derived>`

### Production track

- Triggers only on `android-v<major>.<minor>.<patch>` tag pushes
- Requires `LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED=true`
- Publishes the same signed bundle task with:

  ```bash
  ./gradlew :app:android-main:publishReleaseBundle
  ```

  using:

  - `LOGDATE_PLAY_TRACK=production`
  - `LOGDATE_VERSION_CODE=<tag-derived exact release code>`
  - `LOGDATE_VERSION_NAME=<tag version>`

The Android module defaults the Play track to `internal`, but CI passes the
track explicitly so the workflow behavior stays obvious in one place.

## Local Verification

To verify the Android release bundle still builds locally without publishing:

```bash
./gradlew :app:android-main:bundleRelease \
  -Plogdate.versionCode=1010000 \
  -Plogdate.versionName=1.1.0-local
```

To inspect the Gradle Play Publisher task surface:

```bash
./gradlew :app:android-main:help --task publishReleaseBundle
```

Local publishing is possible too, but it requires the same Play service-account
credentials and release signing inputs as CI.
