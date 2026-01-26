output "cloud_run_service_name" {
  description = "Cloud Run service name."
  value       = google_cloud_run_v2_service.server.name
}

output "cloud_run_service_url" {
  description = "Cloud Run service URL."
  value       = google_cloud_run_v2_service.server.uri
}

output "runtime_service_account_email" {
  description = "Runtime service account email."
  value       = google_service_account.runtime.email
}

output "github_deploy_service_account_email" {
  description = "GitHub Actions deploy service account email."
  value       = local.github_oidc_enabled ? google_service_account.github_deploy[0].email : null
}

output "workload_identity_provider" {
  description = "Workload Identity Provider resource name."
  value       = local.github_oidc_enabled ? google_iam_workload_identity_pool_provider.github[0].name : null
}

output "artifact_registry_repository" {
  description = "Artifact Registry repository ID."
  value       = var.enable_artifact_registry ? google_artifact_registry_repository.logdate[0].id : null
}
