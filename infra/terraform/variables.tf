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
