project_id      = "logdate-staging"
region          = "us-central1"
service_name    = "logdate-server-staging"
cloud_run_image = "us-central1-docker.pkg.dev/logdate-staging/logdate/logdate-server:latest"
webauthn_rp_id  = "cloud-staging.logdate.app"
webauthn_origin = "https://cloud-staging.logdate.app"

enable_github_oidc = true
github_repo        = "WillieCubed/logdate-mobile"

enable_domain_mapping = true
domain                = "cloud-staging.logdate.app"

create_gcs_bucket = true
gcs_bucket_name   = "logdate-media-staging"

cloud_run_env = {
  AUTO_MIGRATE = "true"
}

cloud_run_secret_env = {
  DATABASE_URL = { secret_id = "logdate-db-url" }
  DATABASE_USER = { secret_id = "logdate-db-user" }
  DATABASE_PASSWORD = { secret_id = "logdate-db-password" }
  JWT_SECRET = { secret_id = "logdate-jwt-secret" }
  GOOGLE_OIDC_CLIENT_IDS = { secret_id = "logdate-google-oidc-client-ids" }
  REDIS_URL = { secret_id = "logdate-redis-url" }
}

create_secrets = true
