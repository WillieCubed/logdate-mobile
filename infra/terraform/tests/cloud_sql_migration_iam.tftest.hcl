mock_provider "google" {}
mock_provider "google-beta" {}

run "github_deploy_can_read_every_secret_the_revision_mounts" {
  command = apply

  variables {
    project_id                = "logdate-contract-test"
    cloud_run_image           = "example.invalid/logdate:test"
    github_repo               = "acme/logdate"
    enable_services           = false
    enable_artifact_registry  = false
    enable_cloud_run_service  = false
    create_cloud_sql_instance = false
    cloud_run_secret_env = {
      DATABASE_URL      = { secret_id = "logdate-db-url", version = "1" }
      DATABASE_USER     = { secret_id = "logdate-db-user", version = "1" }
      DATABASE_PASSWORD = { secret_id = "logdate-db-password", version = "1" }
      JWT_SECRET        = { secret_id = "logdate-jwt-secret", version = "1" }
    }
  }

  override_resource {
    target = google_service_account.github_deploy
    values = {
      email = "github-deploy@logdate-contract-test.iam.gserviceaccount.com"
      name  = "projects/logdate-contract-test/serviceAccounts/github-deploy@logdate-contract-test.iam.gserviceaccount.com"
    }
  }

  override_resource {
    target = google_service_account.runtime
    values = {
      email = "logdate-runtime@logdate-contract-test.iam.gserviceaccount.com"
      name  = "projects/logdate-contract-test/serviceAccounts/logdate-runtime@logdate-contract-test.iam.gserviceaccount.com"
    }
  }

  assert {
    condition = alltrue([
      for secret_id in ["logdate-db-url", "logdate-db-user", "logdate-db-password", "logdate-jwt-secret"] :
      google_secret_manager_secret_iam_member.github_migration_access[secret_id].role == "roles/secretmanager.secretAccessor" &&
      google_secret_manager_secret_iam_member.github_migration_access[secret_id].member == "serviceAccount:github-deploy@logdate-contract-test.iam.gserviceaccount.com"
    ])
    error_message = "The GitHub deploy identity must read every secret the revision mounts, so it can migrate and run the startup preflight without Cloud SQL provisioning."
  }

  assert {
    condition     = length(google_project_iam_member.github_secret_metadata_view) == 1 && google_project_iam_member.github_secret_metadata_view["roles/secretmanager.viewer"].member == "serviceAccount:github-deploy@logdate-contract-test.iam.gserviceaccount.com"
    error_message = "The GitHub deploy identity must receive the metadata-only Secret Manager Viewer role needed to discover missing required secret containers."
  }

  assert {
    condition     = length(google_secret_manager_secret_iam_member.github_migration_access) == 4
    error_message = "Secret value access must remain limited to the secrets the revision mounts."
  }

  assert {
    condition     = length(google_project_iam_member.github_cloud_sql_client) == 0
    error_message = "Direct Neon migrations must not require the Cloud SQL Client role."
  }
}

run "github_deploy_has_no_neon_migration_access_without_github_oidc" {
  command = plan

  variables {
    project_id                = "logdate-contract-test"
    cloud_run_image           = "example.invalid/logdate:test"
    github_repo               = ""
    enable_services           = false
    enable_artifact_registry  = false
    enable_cloud_run_service  = false
    create_cloud_sql_instance = false
  }

  assert {
    condition     = length(google_secret_manager_secret_iam_member.github_migration_access) == 0
    error_message = "Neon migration secret access must only be granted to an enabled GitHub OIDC deployment identity."
  }

  assert {
    condition     = length(google_project_iam_member.github_secret_metadata_view) == 0
    error_message = "Neon version metadata access must only be granted to an enabled GitHub OIDC deployment identity."
  }
}
