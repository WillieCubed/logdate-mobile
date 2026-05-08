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
