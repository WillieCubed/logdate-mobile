project_id      = "logdate-dev"
region          = "us-central1"
service_name    = "logdate-server-staging"
# Placeholder image only used for the initial terraform apply. Real images get
# pushed by the Deploy Server Staging workflow (in repo_vars mode for this env).
cloud_run_image = "us-central1-docker.pkg.dev/logdate-dev/logdate/logdate-server:latest"
# WebAuthn rpId is intentionally bound to the staging subdomain rather than the
# registrable apex `logdate.app`. Production uses the apex so passkeys work
# across `*.logdate.app`; if staging used the apex too, a passkey created here
# would also unlock prod (`cloud.logdate.app`). Subdomain-bound rpId keeps the
# two environments isolated.
webauthn_rp_id  = "cloud-staging.logdate.app"
webauthn_origin = "https://cloud-staging.logdate.app"

enable_github_oidc = true
github_repo        = "WillieCubed/logdate-mobile"

enable_domain_mapping = true
domains               = ["cloud-staging.logdate.app"]

create_gcs_bucket = true
gcs_bucket_name   = "logdate-media-staging"

request_concurrency = 16
cpu_idle            = true
startup_cpu_boost   = true

cloud_run_env = {
  LOGDATE_ENV     = "production"
  AUTO_MIGRATE    = "true"
  ALLOWED_ORIGINS = "https://cloud-staging.logdate.app"
  REQUIRE_HTTPS   = "true"
  # Accept passkeys created through the Android Credential Manager (its clientDataJSON.origin
  # is the apk-key-hash, not an https URL). Debug signing cert only on staging; production lists
  # the Play upload + app-signing certs. SHA-256 DF:32:69:...:DB:C7 → base64url apk-key-hash below.
  WEBAUTHN_ALLOWED_ORIGINS = "https://cloud-staging.logdate.app,android:apk-key-hash:3zJp1NzJxP5y_mFioPTp7l8EFEfcs472qSV2_DiQ28c"
  # Colon-hex SHA-256 of the same cert, published in /.well-known/assetlinks.json so Android
  # Credential Manager authorizes the app for the cloud-staging.logdate.app relying party.
  ANDROID_CERT_FINGERPRINTS = "DF:32:69:D4:DC:C9:C4:FE:72:FE:61:62:A0:F4:E9:EE:5F:04:14:47:DC:B3:8E:F6:A9:25:76:FC:38:90:DB:C7"
}

# Secret IDs are scoped to this project's Secret Manager namespace (separate
# from the prod project's identically-named secrets, no collision).
cloud_run_secret_env = {
  DATABASE_URL      = { secret_id = "logdate-db-url" }
  DATABASE_USER     = { secret_id = "logdate-db-user" }
  DATABASE_PASSWORD = { secret_id = "logdate-db-password" }
  JWT_SECRET        = { secret_id = "logdate-jwt-secret" }
  # Mount these only AFTER the matching secret has at least one version.
  # Cloud Run fails the revision if it tries to mount an empty container.
  # Provisioning steps: docs/observability/sentry.md and
  # docs/observability/health-endpoint.md.
  #   SENTRY_DSN              = { secret_id = "logdate-sentry-dsn" }
  #   HEALTH_INTERNAL_TOKEN   = { secret_id = "logdate-health-internal-token" }
  # Opt-in only — populate the secret container then add an entry here:
  #   GOOGLE_OIDC_CLIENT_IDS = { secret_id = "logdate-google-oidc-client-ids" }
  #   REDIS_URL              = { secret_id = "logdate-redis-url" }
}

secret_ids = [
  "logdate-google-oidc-client-ids",
  "logdate-redis-url",
  "logdate-sentry-dsn",
  "logdate-health-internal-token",
]

create_secrets = true
