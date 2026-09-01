project_id   = "logdate-dev"
region       = "us-central1"
service_name = "logdate-server-staging"
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

# This is the isolated non-debug signer used only for staging Cloud builds.
# Play does not yet have a LogDate application or app-signing certificate; the
# production contract therefore remains the only place that can authorize it.
android_signing_certificates = {
  staging = {
    fingerprint         = "DB:66:7C:A5:99:80:91:23:E2:F1:8B:86:98:42:A0:23:1B:52:D5:8B:94:A2:95:76:07:B9:A1:0D:1D:EC:26:88"
    apk_key_hash_origin = "android:apk-key-hash:22Z8pZmAkSPi8YuGmEKgIxtS1YuUopV2B7mhDR3sJog"
  }
}

create_gcs_bucket = true
gcs_bucket_name   = "logdate-media-staging"

request_concurrency = 16
cpu_idle            = true
startup_cpu_boost   = true

cloud_run_env = {
  LOGDATE_ENV                     = "production"
  LOGDATE_EXPECT_FIRST_PARTY      = "true"
  LOGDATE_DEPLOYMENT_KIND         = "first_party"
  LOGDATE_SERVER_DISPLAY_NAME     = "LogDate Cloud (Staging)"
  LOGDATE_PUBLIC_ORIGIN           = "https://cloud-staging.logdate.app"
  ATPROTO_PDS_SERVICE_URL         = "https://cloud-staging.logdate.app"
  ATPROTO_HANDLE_DOMAIN           = "cloud-staging.logdate.app"
  BILLING_PROVIDER                = "play"
  SERVER_ENCRYPTION_ENABLED       = "true"
  SYNC_MEDIA_SIGNED_URLS          = "true"
  SYNC_MEDIA_SIGNED_URL_TTL_HOURS = "1"
  AUTO_MIGRATE                    = "false"
}

# Secret IDs are scoped to this project's Secret Manager namespace (separate
# from the prod project's identically-named secrets, no collision).
cloud_run_secret_env = {
  DATABASE_URL             = { secret_id = "logdate-db-url", version = "1" }
  DATABASE_USER            = { secret_id = "logdate-db-user", version = "1" }
  DATABASE_PASSWORD        = { secret_id = "logdate-db-password", version = "1" }
  JWT_SECRET               = { secret_id = "logdate-jwt-secret", version = "1" }
  SERVER_ENCRYPTION_KEY    = { secret_id = "logdate-server-encryption-key", version = "1" }
  SERVER_ENCRYPTION_KEY_ID = { secret_id = "logdate-server-encryption-key-id", version = "1" }
  HEALTH_INTERNAL_TOKEN    = { secret_id = "logdate-health-internal-token", version = "1" }
  GOOGLE_OIDC_CLIENT_IDS   = { secret_id = "logdate-google-oidc-client-ids", version = "1" }
  # Mount these only AFTER the matching secret has at least one version.
  # Cloud Run fails the revision if it tries to mount an empty container.
  # Provisioning steps: docs/observability/sentry.md.
  #   SENTRY_DSN              = { secret_id = "logdate-sentry-dsn", version = "1" }
  # Opt-in only — populate the secret container then add an entry here:
  #   REDIS_URL              = { secret_id = "logdate-redis-url" }
}

secret_ids = [
  "logdate-google-oidc-client-ids",
  "logdate-redis-url",
  "logdate-sentry-dsn",
  "logdate-health-internal-token",
]

create_secrets = true
