output "cloud_run_service_name" {
  description = "Cloud Run service name."
  value       = var.enable_cloud_run_service ? google_cloud_run_v2_service.server[0].name : null
}

output "cloud_run_service_url" {
  description = "Cloud Run service URL."
  value       = var.enable_cloud_run_service ? google_cloud_run_v2_service.server[0].uri : null
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

output "cloud_sql_connection_name" {
  description = "Cloud SQL connection name."
  value       = var.create_cloud_sql_instance ? google_sql_database_instance.postgres[0].connection_name : null
}

output "cloud_sql_instance_name" {
  description = "Cloud SQL instance name."
  value       = var.create_cloud_sql_instance ? google_sql_database_instance.postgres[0].name : null
}

output "media_bucket_name" {
  description = "Media bucket name."
  value       = var.create_gcs_bucket ? google_storage_bucket.media[0].name : null
}

output "domain_mappings" {
  description = "Map of custom domain to the verifying DNS resource records Cloud Run wants published."
  value = {
    for domain, mapping in google_cloud_run_domain_mapping.default :
    domain => [
      for record in mapping.status[0].resource_records :
      {
        name   = record.name
        rrdata = record.rrdata
        type   = record.type
      }
    ]
  }
}
