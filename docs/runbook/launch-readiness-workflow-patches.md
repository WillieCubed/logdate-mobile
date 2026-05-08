# Launch readiness — GitHub Actions workflow patches

Outstanding patches to the deploy/CI workflows, kept as a worklist. Each
section is independent; apply order doesn't matter. When a patch lands,
remove its section in the same commit.

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
