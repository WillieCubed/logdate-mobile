# Launch readiness — GitHub Actions workflow patches

The repo's `.githooks/block-rogue-configs.sh` blocks direct agent edits to
`.github/workflows/*`. The patches below are proposed alongside the rest of
the launch-readiness work and need to be applied by hand (or via a
sanctioned git commit). Each patch is keyed back to a P0/P1/P2 item from
`/Users/williecubed/.claude/plans/i-need-you-to-lively-frog.md`.

Apply order doesn't matter; each patch is independent.

---

## P0-3 — Run Flyway migrations before Cloud Run rollout

`scripts/run-migrations.sh` exists and is the single entry point. It runs
the Flyway Docker image against Cloud SQL via the auth proxy, reads the
DB user/password from Secret Manager, and (with `--validate-passkey-fk`)
finalizes the V18→V19 orphan-passkey constraint idempotently.

**File:** `.github/workflows/deploy-server.yml`

Insert this step between **Build and push Docker image** and **Deploy to Cloud Run**:

```yaml
      - name: Run database migrations
        # Production runs with AUTO_MIGRATE=false so the server boot path
        # never applies migrations on its own. We run Flyway here, against
        # Cloud SQL via the auth proxy, *before* rolling out the new
        # revision — that guarantees the new code never sees an old schema
        # and that a failed migration aborts the deploy before any traffic
        # touches the new image.
        run: |
          ./scripts/run-migrations.sh \
            --project-id "${PROJECT_ID}" \
            --region "${REGION}" \
            --validate-passkey-fk
```

Notes:
- The script auto-downloads `cloud-sql-proxy` v2.13.2 if not on PATH.
- It uses `docker run flyway/flyway:12.4.0` with `--network host`, so the
  GitHub-hosted runner already has everything needed.
- `--validate-passkey-fk` is a no-op once the constraint has been validated
  once; safe to leave on every deploy.
- A migration failure aborts the workflow before the Cloud Run rollout —
  no traffic touches a new image with an old schema.

---

## P0-7 — Wire `:integration:server-client-e2e:test` into CI

**File:** `.github/workflows/ci.yml`

Add a new job (run in parallel with `unit-tests`):

```yaml
  e2e-tests:
    name: Server / client e2e
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_USER: logdate
          POSTGRES_PASSWORD: logdate
          POSTGRES_DB: logdate
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v3
      - name: Run e2e tests
        env:
          DATABASE_URL: jdbc:postgresql://localhost:5432/logdate
          DATABASE_USER: logdate
          DATABASE_PASSWORD: logdate
        run: ./gradlew :integration:server-client-e2e:test --no-daemon
      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: e2e-test-reports
          path: integration/server-client-e2e/build/reports/tests
```

The harness (`ServerClientE2ETestHarness`) brings up an in-process Ktor
server with a freshly migrated schema and exercises the real
`PasskeyApiClient` and `LogDateCloudApiClient` against it, so this job
catches signup/sync regressions that unit tests miss.

---

## P2-1 — Traffic-split rollback on the server deploy

**File:** `.github/workflows/deploy-server.yml`

Replace the existing **Deploy to Cloud Run** step with a two-phase deploy:
push a no-traffic revision, smoke-test it directly via its revision URL,
then switch traffic. On smoke failure, leave traffic on the previous
revision (auto-rollback by no-action).

```yaml
      - name: Deploy new revision (no traffic)
        id: deploy-revision
        run: |
          gcloud run deploy ${{ env.SERVICE_NAME }} \
            --image ${{ env.REGION }}-docker.pkg.dev/${{ env.PROJECT_ID }}/${{ env.ARTIFACT_REGISTRY_REPO }}/${{ env.SERVICE_NAME }}:${{ env.DEPLOY_SHA }} \
            --platform managed \
            --region ${{ env.REGION }} \
            --project ${{ env.PROJECT_ID }} \
            --no-traffic \
            --tag candidate-${{ env.DEPLOY_SHA }} \
            $SERVICE_ACCOUNT_FLAG

          REVISION_URL=$(gcloud run services describe ${{ env.SERVICE_NAME }} \
            --platform managed --region ${{ env.REGION }} --project ${{ env.PROJECT_ID }} \
            --format='value(status.traffic[?tag=candidate-${{ env.DEPLOY_SHA }}].url)' || true)
          # Fall back to the deterministic revision tag URL if status hasn't
          # caught up yet (Cloud Run sometimes lags by a couple of seconds).
          if [[ -z "$REVISION_URL" ]]; then
            REVISION_URL="https://candidate-${{ env.DEPLOY_SHA }}---$(echo ${{ env.SERVICE_NAME }} | tr A-Z a-z)-$(gcloud run services describe ${{ env.SERVICE_NAME }} --platform managed --region ${{ env.REGION }} --project ${{ env.PROJECT_ID }} --format='value(metadata.labels[cloud.googleapis.com/location])' || echo "")-${{ env.PROJECT_ID }}.a.run.app"
          fi
          echo "REVISION_URL=$REVISION_URL" >> "$GITHUB_ENV"

      - name: Smoke test new revision
        # See P2-2; swap with that step if you adopt both. If smoke fails,
        # don't shift traffic — the old revision keeps serving 100%.
        run: |
          ./scripts/smoke-test-revision.sh "$REVISION_URL"

      - name: Promote new revision to 100% traffic
        run: |
          gcloud run services update-traffic ${{ env.SERVICE_NAME }} \
            --platform managed \
            --region ${{ env.REGION }} \
            --project ${{ env.PROJECT_ID }} \
            --to-tags candidate-${{ env.DEPLOY_SHA }}=100
```

Note: this assumes `scripts/smoke-test-revision.sh` exists (P2-2). If you
adopt P2-2 first, the smoke-test step calls that script; if you don't,
keep at minimum a `curl --fail $REVISION_URL/health` between the no-traffic
deploy and the traffic shift.

---

## P2-2 — Post-deploy smoke test of signup flow

**File added:** `scripts/smoke-test-revision.sh` (already drafted in this
repo — see the script comments for details). It hits `/health`,
`POST /api/v1/auth/signup/passkey/begin` (expecting a `sessionToken` in
the response), and `GET /api/v1/auth/me` with a freshly minted refresh
token (expecting 401, since we have no real session — the goal is just
to confirm the route is wired and not 500-ing).

**File:** `.github/workflows/deploy-server.yml`

If you've adopted P2-1, the smoke step above already calls this script.
Otherwise add this step *after* the existing **Verify deployment** step:

```yaml
      - name: Smoke test signup endpoint
        run: |
          ./scripts/smoke-test-revision.sh "https://${{ env.DOMAIN }}"
```

---

## P2-5 — Inject RELEASE_VERSION into Cloud Run for Sentry attribution

**File:** `.github/workflows/deploy-server.yml`

The server (`Application.kt::initializeSentry`) now reads `RELEASE_VERSION` to
populate the Sentry release tag. Set it during deploy so each revision shows up
distinctly in the Sentry dashboard. Update the **Deploy to Cloud Run** step
(or the new `Deploy new revision (no traffic)` step from P2-1) to include:

```yaml
            --set-env-vars "RELEASE_VERSION=logdate-server@${{ env.DEPLOY_SHA }}" \
```

Without this, every deploy logs `Sentry initialised for production (release=logdate-server@unknown)` —
errors still get captured but you can't tell which build introduced them.

---

## P2-7 — Require manual approval before Android production promotion

**File:** `.github/workflows/publish-android-play.yml`

The `production` job (the one gated on `android-v*` tag push) needs an
`environment:` declaration so a repo reviewer must approve before it runs:

```yaml
  publish-production:
    name: Promote to production track
    needs: publish-internal
    if: startsWith(github.ref, 'refs/tags/android-v')
    environment: android-production   # ← add this line
    runs-on: ubuntu-latest
    # ... existing steps unchanged ...
```

Then in **Settings → Environments → android-production**, add required
reviewers (the Android leads). Without this, `git push origin android-v1.0.0`
auto-promotes the latest internal-track build with no human gate.

---

## Publish — iOS App Store + TestFlight (new workflow)

**File:** `.github/workflows/publish-ios-app-store.yml` (create new)

Mirrors the Android publish workflow shape: push to `main` ships an
internal TestFlight build, push of `ios-v<X.Y.Z>` runs the same archive
+ upload + then submits the build to Apple's review queue, gated by a
required-reviewer GitHub Environment.

The supporting scripts already exist in this PR:

- `scripts/resolve-ios-app-version.sh` — emits `marketing_version` and
  `current_project_version`, plus generates `iosApp/Configuration/Version.xcconfig`.
- `scripts/install-ios-signing.sh` — decodes the `.p12` cert and
  `.mobileprovision` from secrets into a per-run keychain.
- `scripts/submit-ios-for-review.sh` (Python) — polls App Store Connect
  until the build is `VALID`, then submits for review.

`iosApp/Configuration/ExportOptions.plist` carries a
`__PROVISIONING_PROFILE__` placeholder that the workflow substitutes at
runtime with the UUID `install-ios-signing.sh` exposes via
`LOGDATE_IOS_PROVISIONING_PROFILE_UUID`.

**Required GitHub Secrets (six new):**

| Secret | Source |
|---|---|
| `IOS_DISTRIBUTION_CERT_P12_BASE64` | `base64 < distribution.p12` |
| `IOS_DISTRIBUTION_CERT_PASSWORD` | password used at `.p12` export |
| `IOS_PROVISIONING_PROFILE_BASE64` | `base64 < distribution.mobileprovision` |
| `APP_STORE_CONNECT_API_KEY_ID` | App Store Connect → Users and Access → Keys |
| `APP_STORE_CONNECT_API_ISSUER_ID` | same page (top of the Keys list) |
| `APP_STORE_CONNECT_API_KEY_P8` | raw `.p8` content (one-time download when key is minted) |

Also: in **Settings → Environments → ios-production**, add required
reviewers before the first `ios-v*` tag is pushed.

**Workflow YAML** (paste in full):

```yaml
name: Publish iOS

on:
  push:
    branches: [main]
    tags: ['ios-v*']
  workflow_dispatch:
    inputs:
      track:
        description: Distribution track
        type: choice
        default: testflight
        options: [testflight, app-store]

concurrency:
  group: publish-ios-${{ github.ref }}
  cancel-in-progress: false

jobs:
  publish-internal:
    name: Upload to TestFlight Internal
    if: >
      github.event_name == 'push'
      && github.ref == 'refs/heads/main'
    runs-on: macos-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0   # resolve-ios-app-version.sh needs full history

      - name: Resolve iOS version
        id: version
        run: ./scripts/resolve-ios-app-version.sh

      - name: Install signing
        env:
          IOS_DISTRIBUTION_CERT_P12_BASE64: ${{ secrets.IOS_DISTRIBUTION_CERT_P12_BASE64 }}
          IOS_DISTRIBUTION_CERT_PASSWORD: ${{ secrets.IOS_DISTRIBUTION_CERT_PASSWORD }}
          IOS_PROVISIONING_PROFILE_BASE64: ${{ secrets.IOS_PROVISIONING_PROFILE_BASE64 }}
        run: ./scripts/install-ios-signing.sh

      - name: Render ExportOptions.plist with installed profile UUID
        run: |
          /usr/libexec/PlistBuddy \
            -c "Set :provisioningProfiles:studio.hypertext.LogDate $LOGDATE_IOS_PROVISIONING_PROFILE_UUID" \
            iosApp/Configuration/ExportOptions.plist

      - name: Set up JDK 17 (for the Compose Multiplatform framework build)
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'

      - name: Cache Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build Compose Multiplatform iOS framework (release)
        run: ./gradlew :app:compose-main:linkPodReleaseFrameworkIosArm64

      - name: Archive iOS app
        run: |
          xcodebuild archive \
            -project iosApp/iosApp.xcodeproj \
            -scheme iosApp \
            -configuration Release \
            -archivePath build/iosApp.xcarchive \
            -destination 'generic/platform=iOS' \
            -allowProvisioningUpdates NO

      - name: Export .ipa
        run: |
          xcodebuild -exportArchive \
            -archivePath build/iosApp.xcarchive \
            -exportOptionsPlist iosApp/Configuration/ExportOptions.plist \
            -exportPath build/export

      - name: Upload to TestFlight
        env:
          API_KEY_ID: ${{ secrets.APP_STORE_CONNECT_API_KEY_ID }}
          API_ISSUER: ${{ secrets.APP_STORE_CONNECT_API_ISSUER_ID }}
          API_KEY_P8: ${{ secrets.APP_STORE_CONNECT_API_KEY_P8 }}
        run: |
          mkdir -p ~/.appstoreconnect/private_keys
          printf '%s' "$API_KEY_P8" > ~/.appstoreconnect/private_keys/AuthKey_${API_KEY_ID}.p8
          chmod 600 ~/.appstoreconnect/private_keys/AuthKey_${API_KEY_ID}.p8
          xcrun altool --upload-app \
            --type ios \
            --file build/export/iosApp.ipa \
            --apiKey "$API_KEY_ID" \
            --apiIssuer "$API_ISSUER"

      - name: Upload archive + .ipa as artifact
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: ios-internal-${{ steps.version.outputs.current_project_version }}
          if-no-files-found: warn
          path: |
            build/iosApp.xcarchive
            build/export/iosApp.ipa

  publish-production:
    name: Submit to App Store review
    if: startsWith(github.ref, 'refs/tags/ios-v')
    environment: ios-production
    runs-on: macos-latest
    timeout-minutes: 90
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0

      - name: Resolve iOS version
        id: version
        run: ./scripts/resolve-ios-app-version.sh

      - name: Install signing
        env:
          IOS_DISTRIBUTION_CERT_P12_BASE64: ${{ secrets.IOS_DISTRIBUTION_CERT_P12_BASE64 }}
          IOS_DISTRIBUTION_CERT_PASSWORD: ${{ secrets.IOS_DISTRIBUTION_CERT_PASSWORD }}
          IOS_PROVISIONING_PROFILE_BASE64: ${{ secrets.IOS_PROVISIONING_PROFILE_BASE64 }}
        run: ./scripts/install-ios-signing.sh

      - name: Render ExportOptions.plist with installed profile UUID
        run: |
          /usr/libexec/PlistBuddy \
            -c "Set :provisioningProfiles:studio.hypertext.LogDate $LOGDATE_IOS_PROVISIONING_PROFILE_UUID" \
            iosApp/Configuration/ExportOptions.plist

      - name: Set up JDK 17
        uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'

      - name: Cache Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build Compose Multiplatform iOS framework (release)
        run: ./gradlew :app:compose-main:linkPodReleaseFrameworkIosArm64

      - name: Archive iOS app
        run: |
          xcodebuild archive \
            -project iosApp/iosApp.xcodeproj \
            -scheme iosApp \
            -configuration Release \
            -archivePath build/iosApp.xcarchive \
            -destination 'generic/platform=iOS' \
            -allowProvisioningUpdates NO

      - name: Export .ipa
        run: |
          xcodebuild -exportArchive \
            -archivePath build/iosApp.xcarchive \
            -exportOptionsPlist iosApp/Configuration/ExportOptions.plist \
            -exportPath build/export

      - name: Upload to App Store Connect
        env:
          API_KEY_ID: ${{ secrets.APP_STORE_CONNECT_API_KEY_ID }}
          API_ISSUER: ${{ secrets.APP_STORE_CONNECT_API_ISSUER_ID }}
          API_KEY_P8: ${{ secrets.APP_STORE_CONNECT_API_KEY_P8 }}
        run: |
          mkdir -p ~/.appstoreconnect/private_keys
          printf '%s' "$API_KEY_P8" > ~/.appstoreconnect/private_keys/AuthKey_${API_KEY_ID}.p8
          chmod 600 ~/.appstoreconnect/private_keys/AuthKey_${API_KEY_ID}.p8
          xcrun altool --upload-app \
            --type ios \
            --file build/export/iosApp.ipa \
            --apiKey "$API_KEY_ID" \
            --apiIssuer "$API_ISSUER"

      - name: Submit for App Store review
        env:
          APP_STORE_CONNECT_API_KEY_ID: ${{ secrets.APP_STORE_CONNECT_API_KEY_ID }}
          APP_STORE_CONNECT_API_ISSUER_ID: ${{ secrets.APP_STORE_CONNECT_API_ISSUER_ID }}
          APP_STORE_CONNECT_API_KEY_P8: ${{ secrets.APP_STORE_CONNECT_API_KEY_P8 }}
          IOS_BUNDLE_ID: studio.hypertext.LogDate
          MARKETING_VERSION: ${{ steps.version.outputs.marketing_version }}
          CURRENT_PROJECT_VERSION: ${{ steps.version.outputs.current_project_version }}
        run: ./scripts/submit-ios-for-review.sh

      - name: Upload archive + .ipa as artifact
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: ios-production-${{ steps.version.outputs.marketing_version }}
          if-no-files-found: warn
          path: |
            build/iosApp.xcarchive
            build/export/iosApp.ipa
```

Notes:

- The `publish-internal` and `publish-production` jobs don't share a
  cache or a job dependency: a tag push goes straight to production
  archive + upload + submit, intentionally bypassing the Internal
  TestFlight track. App Store rules don't have an analogue to Play's
  "promote internal build to production" REST call, so the simplest
  thing is to re-archive on the tag.
- `concurrency.group` is keyed on `github.ref` so two tag pushes won't
  collide, but a `main` push won't queue behind a `ios-v*` push.
- `actions/setup-node@v6`, `actions/checkout@v6`, `actions/upload-artifact@v7`
  match the bumps already shipped to logdate-web; same node24 wrapper.
- `actions/setup-java@v6` is the latest with node24 internals.

---

## Tag-namespace hygiene — scope `deploy-server.yml`

**File:** `.github/workflows/deploy-server.yml`

Today the deploy workflow triggers on **any** tag push:

```yaml
on:
  push:
    tags:
      - '*'
```

That means pushing `ios-v1.0.0`, `android-v1.0.0`, or `atproto-v1.0.0`
also fires a Cloud Run production deploy — almost certainly not the
intent. Tighten the trigger:

```yaml
on:
  push:
    tags:
      - 'server-v*'    # scope future tags to a server-prefixed pattern
      - 'v*'           # keep the existing semver convention
```

If you want to be even stricter, drop the `'v*'` line and make
operators always use `server-v<X.Y.Z>` going forward. Backwards
compatibility decision is yours.

---

## How to apply

These patches are decoupled from the rest of the launch-readiness work
because the agent harness can't write to `.github/workflows/`. After
reviewing each patch:

1. Apply manually (open the file, paste the snippet).
2. `git add .github/workflows/<file>` and commit.
3. Push to a branch and open a PR; CI will validate the workflow on the
   PR before merging.
4. Delete the corresponding section from this file once landed (and
   delete the file when all patches have shipped).
