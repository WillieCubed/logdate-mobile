provider "google" {
  project = var.project_id
  region  = var.region
}

provider "google-beta" {
  project = var.project_id
  region  = var.region
}

locals {
  # Cloud Run v2 reserves PORT (and the K_* family) and sets it automatically
  # at runtime. Including it in the env block fails with HTTP 400.
  base_env = {
    HOST           = "0.0.0.0"
    GCS_PROJECT_ID = var.project_id
  }

  cloud_sql_env = var.create_cloud_sql_instance ? {
    CLOUD_SQL_INSTANCE_CONNECTION_NAME = google_sql_database_instance.postgres[0].connection_name
    DB_NAME                            = google_sql_database.database[0].name
  } : {}

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
    local.cloud_sql_env,
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

  effective_domains = toset(concat(
    var.domains,
    var.domain != "" ? [var.domain] : [],
  ))

  always_on_services = [
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "secretmanager.googleapis.com",
  ]

  conditional_services = concat(
    var.create_gcs_bucket ? ["storage.googleapis.com"] : [],
    var.create_cloud_sql_instance ? ["sqladmin.googleapis.com"] : [],
  )

  required_services = concat(local.always_on_services, local.conditional_services)
}

resource "google_project_service" "required" {
  for_each = var.enable_services ? toset(local.required_services) : toset([])

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
  count                              = local.github_oidc_enabled ? 1 : 0
  workload_identity_pool_id          = google_iam_workload_identity_pool.github[0].workload_identity_pool_id
  workload_identity_pool_provider_id = "github-provider"
  display_name                       = "GitHub Actions Provider"

  attribute_mapping = {
    "google.subject"             = "assertion.sub"
    "attribute.actor"            = "assertion.actor"
    "attribute.repository"       = "assertion.repository"
    "attribute.repository_owner" = "assertion.repository_owner"
  }

  attribute_condition = "assertion.repository == '${var.github_repo}' && (assertion.ref == 'refs/heads/main' || assertion.ref.startsWith('refs/tags/'))"
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

resource "google_project_iam_member" "runtime_roles" {
  for_each = var.create_cloud_sql_instance ? toset([
    "roles/cloudsql.client"
  ]) : toset([])

  project = var.project_id
  role    = each.key
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_sql_database_instance" "postgres" {
  count               = var.create_cloud_sql_instance ? 1 : 0
  name                = var.cloud_sql_instance_name
  region              = var.region
  database_version    = var.cloud_sql_database_version
  deletion_protection = var.cloud_sql_deletion_protection

  settings {
    tier              = var.cloud_sql_tier
    availability_type = var.cloud_sql_availability_type
    disk_size         = var.cloud_sql_disk_size_gb
    disk_type         = "PD_SSD"

    backup_configuration {
      enabled = true
    }

    insights_config {
      query_insights_enabled = true
    }

    ip_configuration {
      ipv4_enabled = true
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_sql_database" "database" {
  count    = var.create_cloud_sql_instance ? 1 : 0
  instance = google_sql_database_instance.postgres[0].name
  name     = var.cloud_sql_database_name
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
  count    = var.enable_cloud_run_service ? 1 : 0
  name     = var.service_name
  location = var.region
  ingress  = var.ingress
  labels   = var.labels

  template {
    service_account                  = google_service_account.runtime.email
    timeout                          = "${var.timeout_seconds}s"
    max_instance_request_concurrency = var.request_concurrency

    scaling {
      min_instance_count = var.min_instances
      max_instance_count = var.max_instances
    }

    containers {
      image = var.cloud_run_image

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
        cpu_idle          = var.cpu_idle
        startup_cpu_boost = var.startup_cpu_boost
      }

      startup_probe {
        timeout_seconds   = 5
        period_seconds    = 5
        failure_threshold = 12
        http_get {
          path = "/health"
          port = 8080
        }
      }

      liveness_probe {
        initial_delay_seconds = 15
        timeout_seconds       = 5
        period_seconds        = 30
        failure_threshold     = 3
        http_get {
          path = "/health"
          port = 8080
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

  depends_on = [
    google_project_service.required,
    google_secret_manager_secret_iam_member.runtime_access,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  count    = var.allow_unauthenticated && var.enable_cloud_run_service ? 1 : 0
  project  = var.project_id
  location = google_cloud_run_v2_service.server[0].location
  name     = google_cloud_run_v2_service.server[0].name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_domain_mapping" "default" {
  for_each = var.enable_domain_mapping && var.enable_cloud_run_service ? local.effective_domains : toset([])
  provider = google-beta
  location = var.region
  name     = each.key

  metadata {
    namespace = var.project_id
  }

  spec {
    route_name = google_cloud_run_v2_service.server[0].name
  }
}
