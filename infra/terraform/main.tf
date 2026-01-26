provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

locals {
  base_env = {
    PORT           = "8080"
    HOST           = "0.0.0.0"
    GCS_PROJECT_ID = var.project_id
  }

  bucket_env = var.gcs_bucket_name != "" ? {
    GCS_BUCKET_NAME = var.gcs_bucket_name
  } : {}

  webauthn_env = var.webauthn_rp_id != "" ? {
    WEBAUTHN_RP_ID = var.webauthn_rp_id
  } : {}

  webauthn_origin_env = var.webauthn_origin != "" ? {
    WEBAUTHN_ORIGIN = var.webauthn_origin
  } : {}

  env_vars = merge(
    local.base_env,
    local.bucket_env,
    local.webauthn_env,
    local.webauthn_origin_env,
    var.cloud_run_env
  )

  secret_ids = toset(concat(
    tolist(var.secret_ids),
    [for item in values(var.cloud_run_secret_env) : item.secret_id]
  ))

  secret_ids_to_create = var.create_secrets ? local.secret_ids : toset([])

  github_oidc_enabled = var.enable_github_oidc && var.github_repo != ""
}

resource "google_project_service" "required" {
  for_each = var.enable_services ? toset([
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "secretmanager.googleapis.com",
    "storage.googleapis.com"
  ]) : toset([])

  service            = each.key
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "logdate" {
  count = var.enable_artifact_registry ? 1 : 0

  location      = var.region
  repository_id = var.artifact_registry_repo
  description   = "LogDate container images"
  format        = "DOCKER"
  labels        = var.labels

  depends_on = [google_project_service.required]
}

resource "google_service_account" "runtime" {
  account_id   = var.runtime_service_account_name
  display_name = "LogDate Cloud Run runtime"
}

resource "google_service_account" "github_deploy" {
  count        = local.github_oidc_enabled ? 1 : 0
  account_id   = var.github_deploy_service_account_name
  display_name = "LogDate GitHub Actions deploy"
}

resource "google_iam_workload_identity_pool" "github" {
  count                     = local.github_oidc_enabled ? 1 : 0
  workload_identity_pool_id = "logdate-github-pool"
  display_name              = "LogDate GitHub Actions Pool"
  description               = "OIDC pool for GitHub Actions"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  count                               = local.github_oidc_enabled ? 1 : 0
  workload_identity_pool_id           = google_iam_workload_identity_pool.github[0].workload_identity_pool_id
  workload_identity_pool_provider_id  = "github-provider"
  display_name                        = "GitHub Actions Provider"

  attribute_mapping = {
    "google.subject"           = "assertion.sub"
    "attribute.actor"          = "assertion.actor"
    "attribute.repository"     = "assertion.repository"
    "attribute.repository_owner" = "assertion.repository_owner"
  }

  attribute_condition = "assertion.repository == '${var.github_repo}' && (assertion.ref == 'refs/heads/main' || startsWith(assertion.ref, 'refs/tags/'))"
  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

resource "google_project_iam_member" "github_deploy_roles" {
  for_each = local.github_oidc_enabled ? toset([
    "roles/run.admin",
    "roles/artifactregistry.writer",
    "roles/iam.serviceAccountUser"
  ]) : toset([])

  project = var.project_id
  role    = each.key
  member  = "serviceAccount:${google_service_account.github_deploy[0].email}"
}

resource "google_service_account_iam_member" "github_deploy_wif" {
  count              = local.github_oidc_enabled ? 1 : 0
  service_account_id = google_service_account.github_deploy[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.github[0].name}/attribute.repository/${var.github_repo}"
}

resource "google_service_account_iam_member" "github_deploy_runtime_sa" {
  count              = local.github_oidc_enabled ? 1 : 0
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.github_deploy[0].email}"
}

resource "google_storage_bucket" "media" {
  count                       = var.create_gcs_bucket ? 1 : 0
  name                        = var.gcs_bucket_name
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  versioning {
    enabled = true
  }

  labels = var.labels

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.required]
}

resource "google_storage_bucket_iam_member" "media_access" {
  count  = var.create_gcs_bucket ? 1 : 0
  bucket = google_storage_bucket.media[0].name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_secret_manager_secret" "env" {
  for_each = local.secret_ids_to_create

  secret_id = each.key
  replication {
    auto {}
  }

  lifecycle {
    prevent_destroy = true
  }

  depends_on = [google_project_service.required]
}

resource "google_secret_manager_secret_iam_member" "runtime_access" {
  for_each  = local.secret_ids
  secret_id = try(google_secret_manager_secret.env[each.key].secret_id, each.key)
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_cloud_run_v2_service" "server" {
  name     = var.service_name
  location = var.region
  ingress  = var.ingress
  labels   = var.labels

  template {
    service_account = google_service_account.runtime.email
    timeout         = "${var.timeout_seconds}s"

    scaling {
      min_instance_count = var.min_instances
      max_instance_count = var.max_instances
    }

    containers {
      image = var.cloud_run_image

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
      }

      dynamic "env" {
        for_each = local.env_vars
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = var.cloud_run_secret_env
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value.secret_id
              version = env.value.version
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }

  depends_on = [google_project_service.required]
}

resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  count    = var.allow_unauthenticated ? 1 : 0
  project  = var.project_id
  location = google_cloud_run_v2_service.server.location
  name     = google_cloud_run_v2_service.server.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_domain_mapping" "default" {
  count    = var.enable_domain_mapping ? 1 : 0
  provider = google-beta
  location = var.region
  name     = var.domain

  metadata {
    namespace = var.project_id
  }

  spec {
    route_name = google_cloud_run_v2_service.server.name
  }
}
