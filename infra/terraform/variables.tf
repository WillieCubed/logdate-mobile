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
    version   = optional(string, "latest")
  }))
  description = "Secret-backed environment variables for Cloud Run."
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

variable "cloud_sql_user_name" {
  type        = string
  description = "Cloud SQL application username."
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
  description = "Whether to create Cloud Run domain mapping."
  default     = false
}

variable "domain" {
  type        = string
  description = "Custom domain for Cloud Run (e.g., cloud.logdate.app)."
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
