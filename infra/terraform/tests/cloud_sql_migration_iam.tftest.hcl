mock_provider "google" {}
mock_provider "google-beta" {}

run "github_deploy_can_connect_for_cloud_sql_migrations" {
  command = apply

  variables {
    project_id                    = "logdate-contract-test"
    cloud_run_image               = "example.invalid/logdate:test"
    github_repo                   = "acme/logdate"
    enable_services               = false
    enable_artifact_registry      = false
    enable_cloud_run_service      = false
    create_cloud_sql_instance     = true
    cloud_sql_deletion_protection = false
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
    condition     = google_project_iam_member.github_cloud_sql_client["roles/cloudsql.client"].role == "roles/cloudsql.client"
    error_message = "The GitHub deploy identity must have the Cloud SQL Client role when migrations use Cloud SQL."
  }

  assert {
    condition     = google_project_iam_member.github_cloud_sql_client["roles/cloudsql.client"].member == "serviceAccount:github-deploy@logdate-contract-test.iam.gserviceaccount.com"
    error_message = "The Cloud SQL Client role must bind the exact GitHub deploy service account."
  }
}

run "github_deploy_has_no_cloud_sql_role_without_cloud_sql" {
  command = plan

  variables {
    project_id                = "logdate-contract-test"
    cloud_run_image           = "example.invalid/logdate:test"
    github_repo               = "acme/logdate"
    enable_services           = false
    enable_artifact_registry  = false
    enable_cloud_run_service  = false
    create_cloud_sql_instance = false
  }

  assert {
    condition     = length(google_project_iam_member.github_cloud_sql_client) == 0
    error_message = "The GitHub deploy Cloud SQL Client binding must be conditional on Cloud SQL migrations."
  }
}
