# LogDate Cloud production readiness (GCP)

This document defines the production-ready setup for LogDate Cloud on GCP.
Terraform is the long-term source of truth. GitHub Actions + gcloud remains
available for emergency deploys and manual intervention.

## Environments
- **Staging (default)**: Deploys on main branch CI success.
- **Production**: Deploys only from tags.
- **Bootstrap-managed single instance**: Deploys from the managed Cloud Run URL and uses GitHub
  repository variables instead of committed `staging.tfvars` / `production.tfvars`.

## Turnkey bootstrap
Use this when you want a fresh first-party instance with the least manual setup:

```bash
./run deploy:bootstrap --yes --project-id YOUR_PROJECT_ID
```

The bootstrap command is intentionally opinionated:
- GCP-only
- Cloud SQL PostgreSQL instead of an external database
- managed Cloud Run URL instead of a custom domain for first install
- passkey auth ready immediately, Google sign-in optional until client IDs are added
- generated deployment metadata stored under `.logdate/deploy/<project-id>/`

If `gh` is authenticated, bootstrap also sets the GitHub Actions auth secrets and the repository
variables used for ongoing image deploys. That path remains OIDC-based; it does not create
service account keys.

## IaC (endgame)
Terraform lives in `infra/terraform`.

### Terraform workflow
1. Configure `staging.tfvars` / `production.tfvars` with:
   - `project_id`, `region`, `service_name`, `cloud_run_image`
   - `github_repo` for Workload Identity Federation
   - `cloud_run_env` + `cloud_run_secret_env`
   - Commit `production.tfvars` so tag-based deploys can read it in CI.
2. Initialize and apply:
   ```bash
   cd infra/terraform
   terraform init
   terraform apply -var-file=staging.tfvars
   ```
3. Insert secret values into Secret Manager:
   ```bash
   echo -n "value" | gcloud secrets versions add logdate-db-url --data-file=-
   ```
4. For staging/production, use a remote state backend (see `infra/terraform/backend.tf.example`).

### Recommended variables
- `webauthn_rp_id`: `cloud.logdate.app` (staging/prod as appropriate)
- `webauthn_origin`: `https://cloud.logdate.app`
- `create_gcs_bucket`: true for managed media storage
- `cloud_run_secret_env`: map of env var name -> secret ID
- `SYNC_MEDIA_SIGNED_URLS`: true to return short-lived GCS signed URLs for media downloads
- `SYNC_MEDIA_SIGNED_URL_TTL_HOURS`: signed URL TTL in hours (1-24)

## GitHub Actions (fallback and emergency)
The workflow in `.github/workflows/deploy-server.yml` is the emergency path.

The workflow reads `infra/terraform/<env>.tfvars` for project/service metadata
when those files exist. Bootstrap-managed instances can instead use repository variables for
project/service metadata and only require Workload Identity secrets for authentication.

Required GitHub Secrets:
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`

Bootstrap-managed repository variables:
- `LOGDATE_DEPLOY_SOURCE=repo_vars`
- `LOGDATE_PROJECT_ID`
- `LOGDATE_REGION`
- `LOGDATE_SERVICE_NAME`
- `LOGDATE_ARTIFACT_REGISTRY_REPO`
- `LOGDATE_RUNTIME_SERVICE_ACCOUNT`

When `LOGDATE_DEPLOY_SOURCE=repo_vars`, the workflow prefers those repository variables even if
checked-in `staging.tfvars` / `production.tfvars` files still exist.

## Manual gcloud deploy
Use `scripts/deploy-cloud-run.sh` when Terraform or CI is unavailable.
This script defaults to image-only deploys to avoid config drift. Use `CONFIG_MODE=full`
to re-apply runtime config from `infra/terraform/<env>.tfvars`.
Image-only mode expects the service to already exist.

## Health checks
- Runtime `/health` endpoint is used by deploy verification.
- Cloud Run performs startup/readiness checks on the exposed port.

## Post-deploy validation
- Verify `/health` for the Cloud Run URL and custom domain.
- Validate WebAuthn RP ID/origin matches the deployed domain.
- Confirm database connectivity and media uploads.
- If `SYNC_MEDIA_SIGNED_URLS` is enabled, verify download URLs are signed and time-limited.
