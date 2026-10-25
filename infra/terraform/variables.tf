variable "project_id" {
  type        = string
  description = "GCP project ID."
}

variable "region" {
  type        = string
  description = "GCP region for Cloud Run and Artifact Registry."
  default     = "us-central1"
}

variable "service_name" {
  type        = string
  description = "Cloud Run service name."
  default     = "logdate-server"
}

variable "cloud_run_image" {
  type        = string
  description = "Container image URI to deploy."
}

variable "enable_cloud_run_service" {
  type        = bool
  description = "Whether Terraform should manage the Cloud Run service resource."
  default     = true
}

variable "runtime_service_account_name" {
  type        = string
  description = "Service account name for the Cloud Run runtime."
  default     = "logdate-runtime"
}

variable "allow_unauthenticated" {
  type        = bool
  description = "Whether to allow public unauthenticated access."
  default     = true
}

variable "min_instances" {
  type        = number
  description = "Minimum number of Cloud Run instances."
  default     = 0
}

variable "max_instances" {
  type        = number
  description = "Maximum number of Cloud Run instances."
  default     = 10
}

variable "cpu" {
  type        = string
  description = "CPU limit for the container."
  default     = "1"
}

variable "memory" {
  type        = string
  description = "Memory limit for the container."
  default     = "512Mi"
}

variable "timeout_seconds" {
  type        = number
  description = "Request timeout in seconds."
  default     = 60
}

variable "request_concurrency" {
  type        = number
  description = "Maximum concurrent requests per Cloud Run instance. Use 0 to accept Cloud Run's default."
  default     = 80
}

variable "cpu_idle" {
  type        = bool
  description = "Whether CPU is only allocated while requests are actively being served."
  default     = true
}

variable "startup_cpu_boost" {
  type        = bool
  description = "Whether Cloud Run should temporarily boost CPU during instance startup to reduce cold-start latency."
  default     = true
}

variable "ingress" {
  type        = string
  description = "Cloud Run ingress setting."
  default     = "INGRESS_TRAFFIC_ALL"
}

variable "cloud_run_env" {
  type        = map(string)
  description = "Additional plain environment variables for Cloud Run."
  default     = {}
}

variable "cloud_run_secret_env" {
  type = map(object({
    secret_id = string
    version   = string
  }))
  description = "Secret-backed environment variables for Cloud Run, pinned to immutable enabled numeric Secret Manager versions."
  default     = {}

  validation {
    condition = alltrue([
      for binding in values(var.cloud_run_secret_env) :
      can(regex("^[1-9][0-9]*$", binding.version))
    ])
    error_message = "Cloud Run secret versions must be exact positive numeric Secret Manager versions; latest is not allowed."
  }
}

variable "android_signing_certificates" {
  type = object({
    staging = optional(object({
      fingerprint         = string
      apk_key_hash_origin = string
    }))
    upload = optional(object({
      fingerprint         = string
      apk_key_hash_origin = string
    }))
    play_app_signing = optional(object({
      fingerprint         = string
      apk_key_hash_origin = string
    }))
  })
  description = "Public Android certificate fingerprints and matching apk-key-hash origins by signing role."
  default     = {}
}

variable "create_secrets" {
  type        = bool
  description = "Whether to create Secret Manager secrets declared in secret_env."
  default     = false
}

variable "secret_ids" {
  type        = set(string)
  description = "Additional Secret Manager secret IDs to create."
  default     = []
}

variable "webauthn_rp_id" {
  type        = string
  description = "WebAuthn relying party ID (domain)."
  default     = ""
}

variable "webauthn_origin" {
  type        = string
  description = "WebAuthn origin (scheme + domain)."
  default     = ""
}

variable "webauthn_additional_origins" {
  type        = list(string)
  description = "Explicit additional HTTPS browser origins within the configured relying party."
  default     = []

  validation {
    condition = length(distinct(var.webauthn_additional_origins)) == length(var.webauthn_additional_origins) && alltrue([
      for origin in var.webauthn_additional_origins : can(regex("^https://[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$", origin))
    ])
    error_message = "Additional origins must be unique origin-only HTTPS URLs."
  }
}

variable "create_gcs_bucket" {
  type        = bool
  description = "Whether to create a GCS bucket for media."
  default     = false
}

variable "gcs_bucket_name" {
  type        = string
  description = "GCS bucket name for media."
  default     = ""
}

variable "create_cloud_sql_instance" {
  type        = bool
  description = "Whether to create a managed Cloud SQL PostgreSQL instance."
  default     = false
}

variable "cloud_sql_instance_name" {
  type        = string
  description = "Cloud SQL instance name."
  default     = "logdate-db"
}

variable "cloud_sql_database_name" {
  type        = string
  description = "Cloud SQL database name."
  default     = "logdate"
}

variable "cloud_sql_database_version" {
  type        = string
  description = "Cloud SQL database engine version."
  default     = "POSTGRES_16"
}

variable "cloud_sql_tier" {
  type        = string
  description = "Cloud SQL machine tier."
  default     = "db-custom-1-3840"
}

variable "cloud_sql_disk_size_gb" {
  type        = number
  description = "Cloud SQL disk size in GB."
  default     = 20
}

variable "cloud_sql_availability_type" {
  type        = string
  description = "Cloud SQL availability type."
  default     = "ZONAL"
}

variable "cloud_sql_deletion_protection" {
  type        = bool
  description = "Whether Cloud SQL deletion protection is enabled."
  default     = true
}

variable "artifact_registry_repo" {
  type        = string
  description = "Artifact Registry repository ID."
  default     = "logdate"
}

variable "artifact_registry_image_name" {
  type        = string
  description = "Shared Artifact Registry image name used by every first-party environment."
  default     = "logdate-server"

  validation {
    condition     = var.artifact_registry_image_name == "logdate-server"
    error_message = "First-party deployments must use the shared logdate-server image name."
  }
}

variable "enable_artifact_registry" {
  type        = bool
  description = "Whether to create the Artifact Registry repository."
  default     = true
}

variable "enable_github_oidc" {
  type        = bool
  description = "Whether to create Workload Identity Federation for GitHub Actions."
  default     = true
}

variable "github_repo" {
  type        = string
  description = "GitHub repository in owner/repo format."
  default     = ""
}

variable "github_deploy_service_account_name" {
  type        = string
  description = "Service account name used by GitHub Actions to deploy."
  default     = "github-deploy"
}

variable "enable_domain_mapping" {
  type        = bool
  description = "Whether to create Cloud Run domain mappings for every entry in `domains`."
  default     = false
}

variable "enable_load_balancer" {
  type        = bool
  description = "Whether to serve every entry in `domains` through a global external Application Load Balancer. It sends the repository event stream to its own HTTP/1 service, because Cloud Run cannot pass WebSockets to a service that uses end-to-end HTTP/2, and sends everything else to the main service."
  default     = false
}

variable "domains" {
  type        = list(string)
  description = "Custom domains for Cloud Run (e.g., [\"cloud.logdate.app\", \"logdate.hypertext.studio\"]). Every entry becomes its own `google_cloud_run_domain_mapping` pointing at the same service."
  default     = []
}

variable "domain" {
  type        = string
  description = "Legacy single-domain shim for deploy scripts that read `var.domain` via `terraform console`. Prefer `domains`; when both are set, the value of `domain` is merged into the effective domains list."
  default     = ""
}

variable "labels" {
  type        = map(string)
  description = "Labels to apply to resources."
  default     = {}
}

variable "enable_services" {
  type        = bool
  description = "Whether to enable required GCP APIs in the project."
  default     = true
}

variable "cleanup_notification_channels" {
  description = "Cloud Monitoring channel resource names for account cleanup failure and backlog alerts."
  type        = list(string)
  default     = []
}

variable "cleanup_operator_email" {
  description = "Optional operator email channel managed with account cleanup alert policies."
  type        = string
  default     = ""
}

variable "handle_resolution_monitor" {
  description = "Account whose handle Cloud Monitoring resolves every five minutes through `https://<handle>/.well-known/atproto-did`, and the DID that response must contain. Leave null to create no monitor. See docs/reference/handle-resolution.md."
  type = object({
    handle = string
    did    = string
  })
  default = null

  validation {
    condition = var.handle_resolution_monitor == null || (
      can(regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]([a-z0-9-]{0,61}[a-z0-9])?$", var.handle_resolution_monitor.handle)) &&
      can(regex("^did:(plc:[a-z2-7]{24}|web:[a-z0-9.%-]+)$", var.handle_resolution_monitor.did))
    )
    error_message = "handle_resolution_monitor needs a lowercase handle such as name.logdate.app and its did:plc or did:web identifier."
  }
}

variable "handle_resolution_notification_channels" {
  description = "Cloud Monitoring channel resource names alerted when the handle resolution monitor fails. The operator email channel from `cleanup_operator_email` is always added."
  type        = list(string)
  default     = []
}
