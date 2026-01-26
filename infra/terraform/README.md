# LogDate Cloud Run Terraform

This folder codifies the production GCP stack for LogDate Cloud. It is intended as the
long-term source of truth. GitHub Actions + gcloud remains the operational fallback
for emergency deploys.

## What this creates
- Cloud Run service (Gen2) with configurable limits and env vars
- Artifact Registry repository (optional)
- Runtime service account (least-privilege target)
- GitHub Actions Workload Identity Federation (optional)
- Secret Manager secrets (optional, placeholder only)
- GCS bucket for media (optional)
- Cloud Run domain mapping (optional, requires verified domain)

## Required inputs
- `project_id`
- `cloud_run_image` (e.g. `us-central1-docker.pkg.dev/PROJECT/logdate/logdate-server:latest`)

## Quick start
```bash
cd infra/terraform
terraform init
terraform apply \
  -var "project_id=YOUR_PROJECT" \
  -var "cloud_run_image=us-central1-docker.pkg.dev/YOUR_PROJECT/logdate/logdate-server:latest" \
  -var "github_repo=owner/repo"
```

## Environment configuration
This repo treats `*.tfvars` as the shared source of truth for both Terraform and
runbooks (including `scripts/deploy-cloud-run.sh`) and CI. Keep staging/production
config in HCL so automation can read it consistently.

```bash
terraform apply -var-file=staging.tfvars
```

Set environment variables with `cloud_run_env` and secrets with `cloud_run_secret_env`.
The module automatically injects `PORT`, `HOST`, `GCS_PROJECT_ID`, and optionally
`GCS_BUCKET_NAME`, `WEBAUTHN_RP_ID`, `WEBAUTHN_ORIGIN`.

Terraform ignores image changes by default so CI can update images without causing drift.
To pin images explicitly, remove the `ignore_changes` block from `infra/terraform/main.tf`.

Buckets and secrets are protected with `prevent_destroy = true` by default. To allow
deletion, remove those lifecycle blocks in `infra/terraform/main.tf`.

Example `staging.tfvars`:
```hcl
project_id      = "logdate-staging"
region          = "us-central1"
service_name    = "logdate-server-staging"
cloud_run_image = "us-central1-docker.pkg.dev/logdate-staging/logdate/logdate-server:latest"
webauthn_rp_id  = "staging.logdate.app"
webauthn_origin = "https://staging.logdate.app"

enable_github_oidc = true
github_repo        = "your-org/logdate"

create_gcs_bucket = true
gcs_bucket_name   = "logdate-media-staging"

cloud_run_env = {
  AUTO_MIGRATE = "true"
}

cloud_run_secret_env = {
  DATABASE_URL = { secret_id = "logdate-db-url" }
  DATABASE_USER = { secret_id = "logdate-db-user" }
  DATABASE_PASSWORD = { secret_id = "logdate-db-password" }
  JWT_SECRET = { secret_id = "logdate-jwt-secret" }
  REDIS_URL = { secret_id = "logdate-redis-url" }
}

create_secrets = true
```

Use `production.tfvars.example` as a template for production.
Commit only non-sensitive fields; keep secrets in Secret Manager.
CI expects a real `production.tfvars` to exist for tag-based deployments.

## Secrets workflow
- `create_secrets = true` creates empty Secret Manager secrets only.
- Add values separately:
  ```bash
  echo -n "value" | gcloud secrets versions add logdate-db-url --data-file=-
  ```
- Ensure the runtime service account has `roles/secretmanager.secretAccessor`.

## State backend (recommended)
Use a remote state backend for staging/production. Copy `backend.tf.example` to
`backend.tf`, set a GCS bucket, and re-run `terraform init` with migration.

## Domain mapping
Set `enable_domain_mapping = true` and `domain = "cloud.logdate.app"` after verifying
ownership in Google Search Console. Domain mappings can take time to provision SSL.

## Fallback (manual) deploy
Use `scripts/deploy-cloud-run.sh` for emergency deploys with gcloud + Docker. This keeps
Terraform as the endgame while still enabling manual intervention.

By default the script only updates the image (no config drift). To bootstrap or fully
re-apply runtime config from the shared tfvars, run with `CONFIG_MODE=full`.
Image-only mode expects the service to already exist.
