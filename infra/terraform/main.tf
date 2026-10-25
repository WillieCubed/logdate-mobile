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
    INSTANCE_CONNECTION_NAME = google_sql_database_instance.postgres[0].connection_name
    DB_NAME                  = google_sql_database.database[0].name
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

  webauthn_allowed_origins_env = (length(var.webauthn_additional_origins) > 0 || anytrue([for certificate in values(var.android_signing_certificates) : certificate != null])) ? {
    WEBAUTHN_ALLOWED_ORIGINS = join(",", concat(
      [var.webauthn_origin],
      [for role in ["staging", "upload", "play_app_signing"] : var.android_signing_certificates[role].apk_key_hash_origin if try(var.android_signing_certificates[role], null) != null],
      var.webauthn_additional_origins,
    ))
  } : {}

  env_vars = merge(
    local.base_env,
    local.cloud_sql_env,
    local.bucket_env,
    local.webauthn_env,
    local.webauthn_origin_env,
    var.cloud_run_env,
    local.webauthn_allowed_origins_env
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

  load_balancer_enabled = var.enable_load_balancer && var.enable_cloud_run_service
  load_balancer_domains = local.load_balancer_enabled ? local.effective_domains : toset([])

  # The deploy workflow creates this service from the same image and deployment contract as the
  # main service; scripts/render-cloud-run-contract.sh derives the same name.
  repo_event_stream_service_name = "${var.service_name}-repo-events"
  repo_event_stream_path         = "/xrpc/com.atproto.sync.subscribeRepos"

  always_on_services = [
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "secretmanager.googleapis.com",
    "cloudscheduler.googleapis.com",
    "monitoring.googleapis.com",
    "logging.googleapis.com",
  ]

  conditional_services = concat(
    var.create_gcs_bucket ? ["storage.googleapis.com"] : [],
    var.create_cloud_sql_instance ? ["sqladmin.googleapis.com"] : [],
    local.load_balancer_enabled ? ["compute.googleapis.com", "certificatemanager.googleapis.com"] : [],
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
    "roles/logging.viewer",
    "roles/artifactregistry.writer",
    "roles/iam.serviceAccountUser"
  ]) : toset([])

  project = var.project_id
  role    = each.key
  member  = "serviceAccount:${google_service_account.github_deploy[0].email}"
}

resource "google_project_iam_member" "github_cloud_sql_client" {
  for_each = local.github_oidc_enabled && var.create_cloud_sql_instance ? toset([
    "roles/cloudsql.client",
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

# Signed media and backup download URLs are signed by the runtime account itself, through the IAM
# Credentials signBlob API, because Cloud Run has no private key file to sign with. Without this
# grant every signing attempt is denied and downloads fall back to being proxied by the server.
resource "google_service_account_iam_member" "runtime_url_signing" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.runtime.email}"
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

# The deploy job reads every secret the revision mounts, not just the database credentials: it
# runs the container locally as a startup preflight before anything is promoted, and that
# container refuses to boot without its full environment. Granting only the database secrets
# meant the deploy could migrate but never prove the image it had just built would start.
resource "google_secret_manager_secret_iam_member" "github_migration_access" {
  for_each = local.github_oidc_enabled ? local.secret_ids : toset([])

  secret_id = try(google_secret_manager_secret.env[each.key].secret_id, each.key)
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.github_deploy[0].email}"
}

# The manual version-inventory workflow needs to discover whether required
# containers exist before it can list their versions. Viewer grants metadata
# only; it cannot read a secret value. Keep all emitted IDs allowlisted in the
# inventory helper rather than enumerating project secrets.
resource "google_project_iam_member" "github_secret_metadata_view" {
  for_each = local.github_oidc_enabled ? toset(["roles/secretmanager.viewer"]) : toset([])

  project = var.project_id
  role    = "roles/secretmanager.viewer"
  member  = "serviceAccount:${google_service_account.github_deploy[0].email}"
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
        name           = "h2c"
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

    precondition {
      condition = length(var.webauthn_additional_origins) == 0 || (
        var.project_id == "logdate-dev" &&
        var.service_name == "logdate-server-staging"
      )
      error_message = "Additional passkey browser origins require the isolated LogDate staging service and relying party."
    }

    precondition {
      condition = (var.webauthn_rp_id == "" && var.webauthn_origin == "" && length(var.webauthn_additional_origins) == 0) || (
        var.webauthn_rp_id != "" &&
        alltrue([for origin in concat([var.webauthn_origin], var.webauthn_additional_origins) :
          can(regex("^https://[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$", origin)) &&
          (origin == "https://${var.webauthn_rp_id}" || endswith(origin, ".${var.webauthn_rp_id}"))
        ])
      )
      error_message = "Passkey browser origins must use HTTPS within the configured relying party."
    }

    precondition {
      condition = var.service_name != "logdate-server-staging" || (
        var.webauthn_rp_id != "logdate.app" &&
        !endswith("logdate.app", ".${var.webauthn_rp_id}") &&
        !anytrue([for origin in concat([var.webauthn_origin], var.webauthn_additional_origins) :
          contains(["https://app.logdate.app", "https://cloud.logdate.app"], origin)
        ])
      )
      error_message = "Staging must use a distinct relying party without authorizing production browser origins."
    }
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

# The load balancer exists for one reason: the main service runs end-to-end HTTP/2 so that uploads
# larger than Cloud Run's 32 MiB HTTP/1 request limit reach it, and Cloud Run does not pass
# WebSocket upgrades to such a service. The repository event stream is a WebSocket, so a second
# service from the same image serves it over HTTP/1, and the load balancer sends that one path
# there. Cloud Run domain mappings cannot route by path. Both services share the event log in
# Postgres, so either one sees every event.
resource "google_compute_global_address" "load_balancer" {
  count = local.load_balancer_enabled ? 1 : 0
  name  = "${var.service_name}-lb"

  depends_on = [google_project_service.required]
}

resource "google_compute_region_network_endpoint_group" "server" {
  count                 = local.load_balancer_enabled ? 1 : 0
  name                  = "${var.service_name}-neg"
  region                = var.region
  network_endpoint_type = "SERVERLESS"

  cloud_run {
    service = var.service_name
  }

  depends_on = [google_project_service.required]
}

resource "google_compute_region_network_endpoint_group" "repo_event_stream" {
  count                 = local.load_balancer_enabled ? 1 : 0
  name                  = "${local.repo_event_stream_service_name}-neg"
  region                = var.region
  network_endpoint_type = "SERVERLESS"

  cloud_run {
    service = local.repo_event_stream_service_name
  }

  depends_on = [google_project_service.required]
}

resource "google_compute_backend_service" "server" {
  count                 = local.load_balancer_enabled ? 1 : 0
  name                  = "${var.service_name}-backend"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  # Matches the main service's end-to-end HTTP/2.
  protocol = "HTTP2"

  backend {
    group = google_compute_region_network_endpoint_group.server[0].id
  }
}

resource "google_compute_backend_service" "repo_event_stream" {
  count                 = local.load_balancer_enabled ? 1 : 0
  name                  = "${local.repo_event_stream_service_name}-backend"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  protocol              = "HTTP"

  backend {
    group = google_compute_region_network_endpoint_group.repo_event_stream[0].id
  }
}

resource "google_compute_url_map" "load_balancer" {
  count           = local.load_balancer_enabled ? 1 : 0
  name            = "${var.service_name}-lb"
  default_service = google_compute_backend_service.server[0].id

  host_rule {
    hosts        = tolist(local.load_balancer_domains)
    path_matcher = "server"
  }

  path_matcher {
    name            = "server"
    default_service = google_compute_backend_service.server[0].id

    path_rule {
      paths   = [local.repo_event_stream_path]
      service = google_compute_backend_service.repo_event_stream[0].id
    }
  }
}

# DNS authorization lets Certificate Manager issue the certificate before DNS moves to the load
# balancer, so the switch from the domain mapping has no window without a valid certificate.
resource "google_certificate_manager_dns_authorization" "load_balancer" {
  for_each = local.load_balancer_domains
  name     = "${var.service_name}-${replace(each.key, ".", "-")}"
  domain   = each.key

  depends_on = [google_project_service.required]
}

resource "google_certificate_manager_certificate" "load_balancer" {
  count = local.load_balancer_enabled ? 1 : 0
  name  = "${var.service_name}-lb"

  managed {
    domains            = tolist(local.load_balancer_domains)
    dns_authorizations = [for authorization in google_certificate_manager_dns_authorization.load_balancer : authorization.id]
  }
}

resource "google_certificate_manager_certificate_map" "load_balancer" {
  count = local.load_balancer_enabled ? 1 : 0
  name  = "${var.service_name}-lb"

  depends_on = [google_project_service.required]
}

resource "google_certificate_manager_certificate_map_entry" "load_balancer" {
  for_each     = local.load_balancer_domains
  name         = "${var.service_name}-${replace(each.key, ".", "-")}"
  map          = google_certificate_manager_certificate_map.load_balancer[0].name
  hostname     = each.key
  certificates = [google_certificate_manager_certificate.load_balancer[0].id]
}

resource "google_compute_target_https_proxy" "load_balancer" {
  count           = local.load_balancer_enabled ? 1 : 0
  name            = "${var.service_name}-lb"
  url_map         = google_compute_url_map.load_balancer[0].id
  certificate_map = "//certificatemanager.googleapis.com/${google_certificate_manager_certificate_map.load_balancer[0].id}"
}

resource "google_compute_global_forwarding_rule" "load_balancer_https" {
  count                 = local.load_balancer_enabled ? 1 : 0
  name                  = "${var.service_name}-lb-https"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  ip_address            = google_compute_global_address.load_balancer[0].id
  port_range            = "443"
  target                = google_compute_target_https_proxy.load_balancer[0].id
}

# Jobs run independently of request CPU allocation and reuse the server's secret references.
resource "google_cloud_run_v2_job" "account_blob_cleanup" {
  count    = var.enable_cloud_run_service ? 1 : 0
  name     = "${var.service_name}-account-blob-cleanup"
  location = var.region
  template {
    parallelism = 1
    task_count  = 1
    template {
      service_account = google_service_account.runtime.email
      timeout         = "3600s"
      max_retries     = 0
      containers {
        image = var.cloud_run_image
        args  = ["--cleanup-account-blobs"]
        resources {
          limits = { cpu = var.cpu, memory = var.memory }
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
  }
  lifecycle {
    ignore_changes = [template[0].template[0].containers[0].image]
  }
  depends_on = [google_project_service.required, google_secret_manager_secret_iam_member.runtime_access]
}

resource "google_service_account" "cleanup_scheduler" {
  count        = var.enable_cloud_run_service ? 1 : 0
  account_id   = "${var.service_name}-cleanup"
  display_name = "Invoke account blob cleanup job"
}

resource "google_cloud_run_v2_job_iam_member" "cleanup_invoker" {
  count    = var.enable_cloud_run_service ? 1 : 0
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.account_blob_cleanup[0].name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.cleanup_scheduler[0].email}"
}

resource "google_cloud_scheduler_job" "account_blob_cleanup" {
  count            = var.enable_cloud_run_service ? 1 : 0
  name             = "${var.service_name}-account-blob-cleanup"
  region           = var.region
  schedule         = "0 * * * *"
  time_zone        = "Etc/UTC"
  attempt_deadline = "180s"
  retry_config {
    retry_count = 3
  }
  http_target {
    http_method = "POST"
    uri         = "https://run.googleapis.com/v2/projects/${var.project_id}/locations/${var.region}/jobs/${google_cloud_run_v2_job.account_blob_cleanup[0].name}:run"
    body        = base64encode("{}")
    headers     = { "Content-Type" = "application/json" }
    oauth_token {
      service_account_email = google_service_account.cleanup_scheduler[0].email
    }
  }
  depends_on = [google_project_service.required, google_cloud_run_v2_job_iam_member.cleanup_invoker]
}

resource "google_monitoring_notification_channel" "cleanup_operator" {
  count        = var.enable_cloud_run_service && var.cleanup_operator_email != "" ? 1 : 0
  display_name = "${var.service_name} account cleanup operator"
  type         = "email"
  labels       = { email_address = var.cleanup_operator_email }
}

resource "google_monitoring_alert_policy" "account_blob_cleanup_failure" {
  count                 = var.enable_cloud_run_service ? 1 : 0
  display_name          = "${var.service_name} account cleanup job failed"
  combiner              = "OR"
  notification_channels = concat(var.cleanup_notification_channels, google_monitoring_notification_channel.cleanup_operator[*].name)
  conditions {
    display_name = "Cleanup execution failed"
    condition_threshold {
      filter          = "resource.type = \"cloud_run_job\" AND resource.labels.job_name = \"${var.service_name}-account-blob-cleanup\" AND metric.type = \"run.googleapis.com/job/completed_execution_count\" AND metric.labels.result = \"failed\""
      comparison      = "COMPARISON_GT"
      threshold_value = 0
      duration        = "0s"
      aggregations {
        alignment_period   = "300s"
        per_series_aligner = "ALIGN_SUM"
      }
    }
  }
  documentation {
    content   = "P1: inspect the cleanup job configuration and storage/database availability within one business day. Pending work remains durable. See docs/reference/operations/account-blob-cleanup.md."
    mime_type = "text/markdown"
  }
}

resource "google_monitoring_alert_policy" "account_blob_cleanup_overdue" {
  count                 = var.enable_cloud_run_service ? 1 : 0
  display_name          = "${var.service_name} account cleanup older than 24 hours"
  combiner              = "OR"
  notification_channels = concat(var.cleanup_notification_channels, google_monitoring_notification_channel.cleanup_operator[*].name)
  conditions {
    display_name = "Pending cleanup older than 24 hours"
    condition_matched_log {
      filter = "resource.type=\"cloud_run_job\" resource.labels.job_name=\"${var.service_name}-account-blob-cleanup\" textPayload:\"SERVER_ACCOUNT_BLOB_CLEANUP_OVERDUE\""
    }
  }
  alert_strategy {
    notification_rate_limit { period = "3600s" }
  }
  documentation {
    content   = "P1: resolve the account deletion cleanup backlog within one business day. Inspect failed job attempts and rerun the job after restoring access. See docs/reference/operations/account-blob-cleanup.md."
    mime_type = "text/markdown"
  }
}

# Other AT Protocol apps find an account by fetching https://<handle>/.well-known/atproto-did.
# The logdate.app website answers that path for every handle host by asking this server, so the
# check covers the whole route: DNS, the website Worker, and the server's account lookup.
resource "google_monitoring_uptime_check_config" "handle_resolution" {
  count        = var.handle_resolution_monitor != null ? 1 : 0
  display_name = "${var.service_name} handle resolution"
  timeout      = "10s"
  period       = "300s"

  http_check {
    path           = "/.well-known/atproto-did"
    port           = 443
    use_ssl        = true
    validate_ssl   = true
    request_method = "GET"
    accepted_response_status_codes {
      status_value = 200
    }
  }

  monitored_resource {
    type = "uptime_url"
    labels = {
      project_id = var.project_id
      host       = var.handle_resolution_monitor.handle
    }
  }

  content_matchers {
    content = "^${replace(var.handle_resolution_monitor.did, ".", "\\.")}\\s*$"
    matcher = "MATCHES_REGEX"
  }

  depends_on = [google_project_service.required]
}

resource "google_monitoring_alert_policy" "handle_resolution_failing" {
  count                 = var.handle_resolution_monitor != null ? 1 : 0
  display_name          = "${var.service_name} handle resolution failing"
  combiner              = "OR"
  notification_channels = concat(var.handle_resolution_notification_channels, google_monitoring_notification_channel.cleanup_operator[*].name)
  conditions {
    display_name = "${var.handle_resolution_monitor.handle} does not resolve to its DID"
    condition_threshold {
      filter          = "metric.type = \"monitoring.googleapis.com/uptime_check/check_passed\" AND resource.type = \"uptime_url\" AND metric.labels.check_id = \"${google_monitoring_uptime_check_config.handle_resolution[0].uptime_check_id}\""
      comparison      = "COMPARISON_GT"
      threshold_value = 1
      duration        = "600s"
      aggregations {
        alignment_period     = "1200s"
        per_series_aligner   = "ALIGN_NEXT_OLDER"
        cross_series_reducer = "REDUCE_COUNT_FALSE"
        group_by_fields      = ["resource.label.*"]
      }
      trigger {
        count = 1
      }
    }
  }
  documentation {
    content   = "P1: other AT Protocol apps cannot verify logdate.app handles while this fires. Run `./scripts/check-handle-resolution.sh --handle ${var.handle_resolution_monitor.handle} --service-url ${lookup(var.cloud_run_env, "LOGDATE_PUBLIC_ORIGIN", "")}` to see which step fails. See docs/runbook/handle-resolution.md."
    mime_type = "text/markdown"
  }

  lifecycle {
    precondition {
      condition     = length(var.handle_resolution_notification_channels) > 0 || (var.enable_cloud_run_service && var.cleanup_operator_email != "")
      error_message = "The handle resolution alert needs somewhere to send it: set handle_resolution_notification_channels or cleanup_operator_email."
    }
    precondition {
      condition     = startswith(lookup(var.cloud_run_env, "LOGDATE_PUBLIC_ORIGIN", ""), "https://")
      error_message = "The handle resolution alert's runbook needs the server origin: set LOGDATE_PUBLIC_ORIGIN in cloud_run_env."
    }
  }
}
