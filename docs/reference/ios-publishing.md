# iOS App Store Publishing

This document covers the iOS app's automated TestFlight + App Store publishing
path. It explains the repo-level inputs, GitHub Actions workflow, versioning
model, and the helper scripts used to prepare the required secrets.

## Scope

The automated path mirrors the Google Play one in shape, with the differences
Apple's tooling forces:

- archive an iOS `.ipa` and upload to App Store Connect
- ship a TestFlight **internal** build on every push to `main`
- ship a **production** App Store submission only from `ios-v*` tag pushes
- gate the production path behind a required-reviewer GitHub Environment
  (`ios-production`)
- bypass the internal-to-production promote path that Play offers — App Store
  Connect has no analogue, so a tag push re-archives from source

The workflow lives in [`publish-ios-app-store.yml`](../../.github/workflows/publish-ios-app-store.yml).

The Compose Multiplatform iOS framework wiring lives in
[`app/compose-main/build.gradle.kts`](../../app/compose-main/build.gradle.kts);
the Xcode project lives in [`iosApp/`](../../iosApp/).

## Required Secrets

| Purpose | Secret name | Required for | Notes |
| --- | --- | --- | --- |
| Distribution certificate | `IOS_DISTRIBUTION_CERT_P12_BASE64` | Internal + production | Base64 of the Apple Distribution `.p12` exported from Keychain. |
| Distribution certificate password | `IOS_DISTRIBUTION_CERT_PASSWORD` | Internal + production | Password supplied at `.p12` export time. |
| Provisioning profile | `IOS_PROVISIONING_PROFILE_BASE64` | Internal + production | Base64 of the App Store distribution `.mobileprovision`. |
| App Store Connect API key ID | `APP_STORE_CONNECT_API_KEY_ID` | Internal + production | 10-character key identifier from App Store Connect → Users and Access → Keys. |
| App Store Connect API issuer ID | `APP_STORE_CONNECT_API_ISSUER_ID` | Internal + production | UUID at the top of the same Keys page. |
| App Store Connect API private key | `APP_STORE_CONNECT_API_KEY_P8` | Internal + production | Raw `.p8` private key content (one-time download). |
| Firebase iOS configuration | `LOGDATE_IOS_GOOGLE_SERVICE_INFO_PLIST_BASE64` | Internal + production | Base64 of `iosApp/iosApp/GoogleService-Info.plist`. Materialized by the [`setup-firebase-configs`](../../.github/actions/setup-firebase-configs/action.yml) composite action. |

The workflow materializes the keychain, provisioning profile, and Firebase
plist at runtime. The keychain is per-job and lives in `$RUNNER_TEMP`, which
GitHub deletes when the job ends.

## Required GitHub Environment

The production-track job declares `environment: ios-production`. Create that
environment under **Settings → Environments → New environment**, then add
the iOS leads as required reviewers. Without reviewers configured, an
`ios-v*` tag push will queue the production job indefinitely waiting for
approval — which is the desired behavior, not a bug.

## Helper Scripts

Three scripts cooperate to prepare a clean publish:

```bash
./scripts/sync-firebase-configs.sh ios          # uploads GoogleService-Info.plist as base64 secret
./scripts/install-ios-signing.sh                # CI-only — decodes cert + profile into a per-run keychain
./scripts/resolve-ios-app-version.sh            # CI-only — emits MARKETING_VERSION + CURRENT_PROJECT_VERSION
./scripts/submit-ios-for-review.sh              # CI-only — submits an uploaded build to App Store review
```

`sync-firebase-configs.sh ios` is the operator-side upload (run locally with
`gh` authenticated). The other three run inside the GitHub Actions runner.

The six App Store Connect / signing secrets do not yet have a `gh secret set`
helper analogous to the Android keystore script — set them by hand:

```bash
base64 -i ~/Downloads/distribution.p12 | gh secret set IOS_DISTRIBUTION_CERT_P12_BASE64
base64 -i ~/Downloads/distribution.mobileprovision | gh secret set IOS_PROVISIONING_PROFILE_BASE64
gh secret set IOS_DISTRIBUTION_CERT_PASSWORD              # prompts for value
gh secret set APP_STORE_CONNECT_API_KEY_ID                # prompts for value
gh secret set APP_STORE_CONNECT_API_ISSUER_ID             # prompts for value
gh secret set APP_STORE_CONNECT_API_KEY_P8 < AuthKey_*.p8
```

## Versioning Model

iOS versioning derives from git history via:

```bash
./scripts/resolve-ios-app-version.sh
```

The script expects iOS release tags in this format:

- `ios-v<major>.<minor>.<patch>`

Examples:

- `ios-v0.1.0`
- `ios-v1.4.2`

Rules:

1. The latest reachable `ios-v*` tag defines the base `MARKETING_VERSION`.
2. `CURRENT_PROJECT_VERSION` (Apple's build number) is commit-stable and does
   not depend on whether a release tag exists yet for that commit.
3. The high-significance digits come from the reachable commit count.
4. The low-significance digits are a deterministic fragment derived from the
   short commit SHA.
5. The script writes `iosApp/Configuration/Version.xcconfig` that the Xcode
   build picks up via the `#include?` in
   [`Config.xcconfig`](../../iosApp/Configuration/Config.xcconfig). The
   include is optional so local Xcode opens cleanly before the script has
   ever run.
6. The first-ever App Store upload must still be done manually via Xcode or
   `xcrun altool` — App Store Connect must be aware of the bundle ID and
   have an app record before the API will accept uploads.

## Workflow Behavior

The workflow has two publish paths:

### TestFlight Internal track

- Triggers on every push to `main`
- Runs on `macos-latest`
- Materializes Firebase plist + keychain + version
- Builds the Compose Multiplatform iOS framework via
  `./gradlew :app:compose-main:linkPodReleaseFrameworkIosArm64`
- Archives the iOS app via `xcodebuild archive`
- Exports the `.ipa` via `xcodebuild -exportArchive`
- Uploads to TestFlight via `xcrun altool --upload-app`
- Uploads the `.xcarchive` + `.ipa` as a workflow artifact (7-day retention)
  for QA pickup

### App Store production track

- Triggers only on `ios-v<major>.<minor>.<patch>` tag pushes
- Requires `ios-production` Environment approval before any work begins
- Re-runs the entire archive + upload pipeline (App Store Connect has no
  promote-from-internal API)
- After upload, polls App Store Connect via
  [`submit-ios-for-review.py`](../../scripts/submit-ios-for-review.py) until
  the build's `processingState` becomes `VALID`, then attaches the build to
  an `appStoreVersion` for the matching `MARKETING_VERSION` and POSTs to
  `/v1/appStoreVersionSubmissions`
- Creates a draft GitHub Release tied to the triggering tag with
  auto-generated notes from commit history; attaches the `.ipa` so the
  exact binary that submitted is downloadable

The production path bypasses the TestFlight Internal job entirely. Because
App Store Connect has no analogue to Play's `promoteReleaseArtifact`, the
simplest correct thing is to re-archive on the tag.

## Submit-for-review Mechanics

`scripts/submit-ios-for-review.py` is the closing step of the production
path. Sequence:

1. Mint an ES256 JWT for the App Store Connect API (20-minute lifetime,
   refreshed per HTTP call).
2. Look up the app by bundle ID (`studio.hypertext.LogDate`).
3. Find the build matching `MARKETING_VERSION` + `CURRENT_PROJECT_VERSION`.
4. Wait for Apple's processing pipeline to mark the build `VALID` (typically
   5–10 minutes after upload).
5. Find or create an `appStoreVersion` for `MARKETING_VERSION` on iOS.
6. Attach the build to that `appStoreVersion`.
7. POST `appStoreVersionSubmissions` to put the build into Apple's review
   queue.

Each HTTP call uses a fresh short-lived JWT. The script bails on the first
non-2xx response so a partial submission doesn't fail silently. A 409 on
the submission POST is treated as success so reruns are idempotent.

## Local Verification

To verify the iOS framework still builds locally without publishing:

```bash
./gradlew :app:compose-main:linkPodReleaseFrameworkIosArm64
```

To verify the signing flow without uploading:

```bash
xcodebuild archive \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -archivePath build/iosApp.xcarchive \
  -destination 'generic/platform=iOS'
```

Local upload to TestFlight is possible but requires the same App Store
Connect API key + distribution cert + provisioning profile that CI uses,
plus a Mac with Xcode installed.

## Troubleshooting

**"Build did not become VALID after timeout"** — Apple's processing pipeline
is slow during peak hours; the script's default timeout is 20 minutes. Set
`SUBMIT_TIMEOUT_SECONDS=2400` (40 minutes) in the workflow env if this
recurs.

**"No matching provisioning profile"** — the `.mobileprovision` secret is
likely stale. The runtime UUID exposed by `install-ios-signing.sh` as
`LOGDATE_IOS_PROVISIONING_PROFILE_UUID` must match the one Apple has on file
for the bundle ID. Re-download from Apple Developer Portal and re-upload
the secret.

**"Authentication failed"** — `APP_STORE_CONNECT_API_KEY_P8` was likely
mis-pasted (the `-----BEGIN PRIVATE KEY-----` block must be intact, with
literal newlines preserved). GitHub's secret editor handles multi-line
content correctly when pasted; if it ever gets normalized to single-line,
re-paste from the original `.p8` file.
