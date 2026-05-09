# Staging and Production Configuration

This is the operating contract for LogDate's server and shipped app builds.
If a workflow, Gradle property, Firebase file, or Terraform value disagrees
with this page, treat it as a deployment blocker until the mismatch is
resolved.

## Server Environments

| Concern | Staging | Production |
| --- | --- | --- |
| Deploy workflow | `.github/workflows/deploy-server-staging.yml` | `.github/workflows/deploy-server-production.yml` |
| Trigger | Successful `CI` workflow on `main` | `server-v*` tag push only |
| GitHub Environment | `server-staging` | `server-production` |
| Terraform file | `infra/terraform/staging.tfvars` | `infra/terraform/production.tfvars` |
| GCP project | `logdate-dev` | `logdate` |
| Cloud Run service | `logdate-server-staging` | `logdate-server` |
| Public host | `cloud-staging.logdate.app` | `cloud.logdate.app` |
| WebAuthn RP ID | `cloud-staging.logdate.app` | `logdate.app` |
| WebAuthn origin | `https://cloud-staging.logdate.app` | `https://cloud.logdate.app` |
| Auto migrations | `AUTO_MIGRATE=true` | `AUTO_MIGRATE=false` |
| Allowed origins | `https://cloud-staging.logdate.app` | `https://cloud.logdate.app` |
| Secret storage | Staging project's Secret Manager | Production project's Secret Manager |
| Media bucket | `logdate-media-staging` | `logdate-media-logdate` |

Production deploys are deliberately tag-gated and staging-verified. The
production workflow checks for a successful staging deploy for the same SHA,
then runs the passkey smoke test against `https://cloud-staging.logdate.app`
before invoking the reusable Cloud Run deploy workflow.

`LOGDATE_DEPLOY_SOURCE` controls where deploy workflows read Terraform-style
configuration:

- `repo_files` reads the committed `infra/terraform/<env>.tfvars` files.
- `repo_vars` reads GitHub repository variables created by bootstrap tooling.
- If both are present, the workflow mode decides precedence; do not assume
  repo variables silently override committed tfvars.

The committed tfvars files must contain only non-sensitive configuration:
project IDs, service names, domains, Secret Manager secret IDs, and runtime
flags. Secret values belong only in Secret Manager or GitHub Actions secrets.

## Server Safety Rules

- Do not share database URLs, JWT secrets, health tokens, Redis URLs, Sentry
  DSNs, or media buckets between staging and production.
- Do not change production `AUTO_MIGRATE=false` without a reviewed rollback
  plan. Production migrations should be explicit and observable.
- Do not set staging `webauthn_rp_id` to `logdate.app`. Staging uses
  `cloud-staging.logdate.app` so passkeys created in staging cannot unlock
  production.
- Do not add non-Cloud-Run domains to Terraform `domains`. Marketing redirects
  and unrelated DNS live outside this Cloud Run service.
- Do not mount optional Cloud Run secrets until the Secret Manager container
  has at least one version. Cloud Run rejects revisions with empty mounted
  secrets.

## App Builds

| Concern | Android debug / CI | Android Play internal | Android Play production | iOS TestFlight | iOS App Store | Desktop |
| --- | --- | --- | --- | --- | --- | --- |
| Workflow | `ci.yml`, `screenshot-test.yml` | `publish-android-play.yml` | `publish-android-play.yml` | `publish-ios-app-store.yml` | `publish-ios-app-store.yml` | Local Gradle tasks |
| Trigger | PR/push/manual test workflows | `main` push or manual dispatch | `android-v*` tag push | `main` push or manual dispatch | `ios-v*` tag push | Manual |
| Human gate | None | Disabled unless repo var is true | `android-production` reviewers | Disabled unless repo var is true | `ios-production` reviewers | None |
| Firebase input | `LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_DEBUG_BASE64` | `LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_RELEASE_BASE64` | None; promotes tested internal artifact | `LOGDATE_IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64` | `LOGDATE_IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64` | None |
| Firebase file on runner | `app/android-main/google-services.json` | `app/android-main/src/release/google-services.json` | Not materialized | `iosApp/iosApp/Firebase/GoogleService-Info-Release.plist` | `iosApp/iosApp/Firebase/GoogleService-Info-Release.plist` | None |
| Signing | Debug signing | Release keystore from GitHub secrets | No keystore; promotes existing Play artifact | Apple distribution cert/profile | Apple distribution cert/profile | Local unsigned packages |
| Backend default | `https://cloud.logdate.app` unless overridden | `https://cloud.logdate.app` | Same tested artifact | `https://cloud.logdate.app` via shared config | Same source as TestFlight | `https://cloud.logdate.app` via shared config |

The shared app default is production cloud:

- `shared/config/.../LogDateConfigRepository.kt` defaults to
  `https://cloud.logdate.app`.
- `gradle.properties` sets `logdate.origin=logdate.app` and
  `logdate.apiBaseUrl=https://logdate.app` for generated app BuildConfig
  values used by deep links and origin classification.
- Override `logdate.origin` or `logdate.apiBaseUrl` in `local.properties`, CI
  Gradle properties, or an explicit local Gradle invocation when testing a
  non-production backend.

This default is intentional for shipped builds: users should not land on
staging by accident. It also means staging app testing must be explicit; never
assume a debug, desktop, or simulator build is pointed at staging unless the
override is visible in the command or local config.

## App Safety Rules

- Android CI and screenshot workflows use the debug Firebase project. Android
  Play internal and production release bundles use the release Firebase
  project because both paths operate on the `release` build type.
- Android production does not rebuild. It promotes the exact internal Play
  artifact for the derived version code, preserving provenance.
- iOS Debug builds run without Firebase. iOS Release/TestFlight/App Store
  builds require `GoogleService-Info-Release.plist` in the path the Xcode
  build phase copies.
- iOS production re-archives from source because App Store Connect has no Play
  style promote-from-internal API. The `ios-production` GitHub Environment is
  the required human gate.
- Desktop has no publishing workflow in this repo. Treat desktop packages as
  local verification artifacts until a signed desktop release workflow exists.
- Firebase config files and signing materials are gitignored; if one appears
  in `git status`, stop and remove it before committing.

## Pre-Release Checks

Before a production server tag:

1. Confirm `CI` and `Deploy Server Staging` are green for the target SHA.
2. Confirm the staging smoke test used `https://cloud-staging.logdate.app`.
3. Confirm production tfvars still have `AUTO_MIGRATE=false` and
   `webauthn_rp_id="logdate.app"`.

Before an Android production tag:

1. Confirm the same commit/version code is already on Play internal.
2. Confirm `LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED=true` only when reviewers
   are ready.
3. Confirm `android-production` has required reviewers.

Before an iOS production tag:

1. Confirm the TestFlight build path succeeded for the target commit.
2. Confirm `LOGDATE_IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64` came from
   `iosApp/iosApp/Firebase/GoogleService-Info-Release.plist`.
3. Confirm the distribution cert and provisioning profile are not within
   30 days of expiry.
