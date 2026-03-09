#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TF_DIR="${REPO_ROOT}/infra/terraform"

PROVIDER="gcp"
YES="false"
INSTANCE_NAME="${INSTANCE_NAME:-}"
PROJECT_ID="${GCP_PROJECT_ID:-${PROJECT_ID:-}}"
BILLING_ACCOUNT="${BILLING_ACCOUNT_ID:-${BILLING_ACCOUNT:-}}"
REGION="${GCP_REGION:-${REGION:-us-central1}}"
SERVICE_NAME="${CLOUD_RUN_SERVICE_NAME:-${SERVICE_NAME:-logdate-server}}"
ARTIFACT_REGISTRY_REPO="${ARTIFACT_REGISTRY_REPO:-logdate}"
IMAGE_TAG="${IMAGE_TAG:-}"
STATE_BUCKET="${TF_STATE_BUCKET:-}"
GITHUB_REPO="${GITHUB_REPO:-}"
CLOUD_SQL_INSTANCE_NAME="${CLOUD_SQL_INSTANCE_NAME:-logdate-db}"
CLOUD_SQL_DATABASE_NAME="${CLOUD_SQL_DATABASE_NAME:-logdate}"
CLOUD_SQL_USER_NAME="${CLOUD_SQL_USER_NAME:-logdate}"
DEPLOY_SOURCE_MODE="repo_vars"
declare -a GITHUB_MANUAL_STEPS=()
GITHUB_WIRING_STATUS="not_attempted"

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1"
        exit 1
    fi
}

detect_github_repo() {
    if [[ -n "$GITHUB_REPO" ]]; then
        return
    fi

    local repo_url
    repo_url="$(git -C "$REPO_ROOT" config --get remote.origin.url 2>/dev/null || true)"
    if [[ "$repo_url" =~ github\.com[:/]([^/]+/[^/.]+) ]]; then
        GITHUB_REPO="${BASH_REMATCH[1]}"
    fi
}

sanitize_slug() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9-' '-' | sed -e 's/^-//' -e 's/-$//'
}

default_project_id() {
    local user_slug timestamp max_base_len base candidate
    user_slug="$(sanitize_slug "${USER:-dev}")"
    user_slug="${user_slug:-dev}"
    timestamp="$(date +%y%m%d%H%M)"
    max_base_len=$((30 - 1 - ${#timestamp}))
    base="logdate-${user_slug}"
    base="${base:0:$max_base_len}"
    base="${base%-}"
    candidate="${base}-${timestamp}"
    candidate="${candidate:0:30}"
    candidate="${candidate%-}"
    if [[ ! "$candidate" =~ ^[a-z] ]]; then
        candidate="l${candidate}"
        candidate="${candidate:0:30}"
        candidate="${candidate%-}"
    fi
    while ((${#candidate} < 6)); do
        candidate="${candidate}0"
    done
    printf '%s' "$candidate"
}

validate_project_id() {
    local project_id="$1"
    if [[ ! "$project_id" =~ ^[a-z][a-z0-9-]{4,28}[a-z0-9]$ ]]; then
        echo "Invalid GCP project ID: $project_id"
        echo "Project IDs must be 6-30 chars, lowercase letters/digits/hyphens, start with a letter, and not end with a hyphen."
        exit 1
    fi
}

choose_billing_account() {
    if [[ -n "$BILLING_ACCOUNT" ]]; then
        return
    fi

    local accounts
    accounts="$(gcloud billing accounts list --filter='open=true' --format='value(name)' 2>/dev/null || true)"
    local count
    count="$(printf '%s\n' "$accounts" | sed '/^$/d' | wc -l | tr -d ' ')"

    if [[ "$count" == "1" ]]; then
        BILLING_ACCOUNT="$(printf '%s\n' "$accounts" | sed '/^$/d' | head -n 1)"
        BILLING_ACCOUNT="${BILLING_ACCOUNT#billingAccounts/}"
        return
    fi

    if [[ "$YES" == "true" ]]; then
        echo "Multiple or no open billing accounts found. Pass --billing-account in headless mode."
        exit 1
    fi

    echo "Available billing accounts:"
    printf '%s\n' "$accounts" | sed '/^$/d' | nl -w2 -s') '
    read -r -p "Billing account ID: " BILLING_ACCOUNT
    if [[ -z "$BILLING_ACCOUNT" ]]; then
        echo "Billing account is required."
        exit 1
    fi
    BILLING_ACCOUNT="${BILLING_ACCOUNT#billingAccounts/}"
}

gcloud_link_billing() {
    local project_id="$1" billing_account="$2"
    if gcloud billing projects link "$project_id" --billing-account="$billing_account" >/dev/null 2>&1; then
        return
    fi
    gcloud beta billing projects link "$project_id" --billing-account="$billing_account" >/dev/null
}

enable_bootstrap_apis() {
    local project_id="$1"
    gcloud services enable \
        --project="$project_id" \
        serviceusage.googleapis.com \
        cloudresourcemanager.googleapis.com \
        iam.googleapis.com \
        iamcredentials.googleapis.com \
        run.googleapis.com \
        artifactregistry.googleapis.com \
        secretmanager.googleapis.com \
        sqladmin.googleapis.com \
        storage.googleapis.com >/dev/null
}

generate_base64_secret() {
    openssl rand -base64 32 | tr -d '\n'
}

generate_password_secret() {
    openssl rand -hex 24 | tr -d '\n'
}

secret_latest_value() {
    local secret_id="$1"
    gcloud secrets versions access latest --secret "$secret_id" --project="$PROJECT_ID" 2>/dev/null || true
}

add_secret_version() {
    local secret_id="$1" value="$2"
    printf '%s' "$value" | gcloud secrets versions add "$secret_id" --project="$PROJECT_ID" --data-file=- >/dev/null
}

ensure_secret_value() {
    local secret_id="$1" generator="$2"
    local existing
    existing="$(secret_latest_value "$secret_id")"
    if [[ -n "$existing" ]]; then
        printf '%s' "$existing"
        return
    fi

    local generated
    generated="$("$generator")"
    add_secret_version "$secret_id" "$generated"
    printf '%s' "$generated"
}

ensure_literal_secret_value() {
    local secret_id="$1" literal_value="$2"
    local existing
    existing="$(secret_latest_value "$secret_id")"
    if [[ -n "$existing" ]]; then
        printf '%s' "$existing"
        return
    fi

    add_secret_version "$secret_id" "$literal_value"
    printf '%s' "$literal_value"
}

ensure_cloud_sql_user() {
    local existing_users
    existing_users="$(
        gcloud sql users list \
            --project="$PROJECT_ID" \
            --instance="$CLOUD_SQL_INSTANCE_NAME" \
            --format='value(name)' 2>/dev/null || true
    )"

    if printf '%s\n' "$existing_users" | grep -Fxq "$CLOUD_SQL_USER_NAME"; then
        gcloud sql users set-password "$CLOUD_SQL_USER_NAME" \
            --project="$PROJECT_ID" \
            --instance="$CLOUD_SQL_INSTANCE_NAME" \
            --password="$1" >/dev/null
        return
    fi

    gcloud sql users create "$CLOUD_SQL_USER_NAME" \
        --project="$PROJECT_ID" \
        --instance="$CLOUD_SQL_INSTANCE_NAME" \
        --password="$1" >/dev/null
}

bootstrap_runtime_secrets() {
    ensure_literal_secret_value "logdate-db-user" "$CLOUD_SQL_USER_NAME" >/dev/null
    local db_pw
    db_pw="$(ensure_secret_value "logdate-db-password" generate_password_secret)"
    ensure_secret_value "logdate-jwt-secret" generate_base64_secret >/dev/null
    ensure_secret_value "logdate-server-encryption-key" generate_base64_secret >/dev/null
    ensure_literal_secret_value "logdate-server-encryption-key-id" "${SERVICE_NAME}-v1" >/dev/null
    ensure_cloud_sql_user "$db_pw"
}

write_backend_config() {
    local backend_path="$1"
    cat >"$backend_path" <<EOF
bucket = "${STATE_BUCKET}"
prefix = "logdate/bootstrap/${PROJECT_ID}"
EOF
}

write_tfvars() {
    local tfvars_path="$1"
    local service_url="${2:-}"
    local service_host="${3:-}"
    local connection_name="${PROJECT_ID}:${REGION}:${CLOUD_SQL_INSTANCE_NAME}"
    local enable_github_oidc_json='false'
    local github_repo_line=""
    if [[ -n "$GITHUB_REPO" ]]; then
        enable_github_oidc_json='true'
        github_repo_line="  \"github_repo\": \"${GITHUB_REPO}\","
    fi

    local webauthn_rp_id_json='""'
    local webauthn_origin_json='""'
    local public_origin_env=""
    if [[ -n "$service_url" && -n "$service_host" ]]; then
        webauthn_rp_id_json="\"${service_host}\""
        webauthn_origin_json="\"${service_url}\""
        public_origin_env=$(
            cat <<EOF
    "LOGDATE_PUBLIC_ORIGIN": "${service_url}",
    "ATPROTO_PDS_SERVICE_URL": "${service_url}",
    "ATPROTO_HANDLE_DOMAIN": "${service_host}",
EOF
        )
    fi

    cat >"$tfvars_path" <<EOF
{
  "project_id": "${PROJECT_ID}",
  "region": "${REGION}",
  "service_name": "${SERVICE_NAME}",
  "cloud_run_image": "us-docker.pkg.dev/cloudrun/container/hello",
  "enable_cloud_run_service": false,
  "enable_github_oidc": ${enable_github_oidc_json},
${github_repo_line}  "artifact_registry_repo": "${ARTIFACT_REGISTRY_REPO}",
  "enable_domain_mapping": false,
  "create_gcs_bucket": true,
  "gcs_bucket_name": "${PROJECT_ID}-media",
  "create_cloud_sql_instance": true,
  "cloud_sql_instance_name": "${CLOUD_SQL_INSTANCE_NAME}",
  "cloud_sql_database_name": "${CLOUD_SQL_DATABASE_NAME}",
  "cloud_sql_user_name": "${CLOUD_SQL_USER_NAME}",
  "create_secrets": true,
  "secret_ids": ["logdate-google-oidc-client-ids"],
  "webauthn_rp_id": ${webauthn_rp_id_json},
  "webauthn_origin": ${webauthn_origin_json},
  "cloud_run_env": {
    "AUTO_MIGRATE": "true",
    "LOGDATE_DEPLOYMENT_KIND": "first_party",
    "LOGDATE_SERVER_DISPLAY_NAME": "LogDate Cloud",
    "WEBAUTHN_STRICT_VERIFICATION": "true",
    "SERVER_ENCRYPTION_ENABLED": "true",
    "SYNC_MEDIA_SIGNED_URLS": "true",
    "SYNC_MEDIA_SIGNED_URL_TTL_HOURS": "1",
    ${public_origin_env}
    "CLOUD_SQL_INSTANCE_CONNECTION_NAME": "${connection_name}"
  },
  "cloud_run_secret_env": {
    "DATABASE_USER": { "secret_id": "logdate-db-user" },
    "DATABASE_PASSWORD": { "secret_id": "logdate-db-password" },
    "JWT_SECRET": { "secret_id": "logdate-jwt-secret" },
    "SERVER_ENCRYPTION_KEY": { "secret_id": "logdate-server-encryption-key" },
    "SERVER_ENCRYPTION_KEY_ID": { "secret_id": "logdate-server-encryption-key-id" }
  }
}
EOF
}

write_manifest() {
    local manifest_path="$1" service_url="$2" service_host="$3" workload_identity_provider="$4" github_service_account="$5" runtime_service_account="$6"
    cat >"$manifest_path" <<EOF
{
  "provider": "gcp",
  "projectId": "${PROJECT_ID}",
  "region": "${REGION}",
  "serviceName": "${SERVICE_NAME}",
  "serviceUrl": "${service_url}",
  "serviceHost": "${service_host}",
  "artifactRegistryRepo": "${ARTIFACT_REGISTRY_REPO}",
  "stateBucket": "${STATE_BUCKET}",
  "tfvarsPath": ".logdate/deploy/${PROJECT_ID}/bootstrap.tfvars.json",
  "backendPath": ".logdate/deploy/${PROJECT_ID}/backend.hcl",
  "workloadIdentityProvider": "${workload_identity_provider}",
  "githubServiceAccount": "${github_service_account}",
  "runtimeServiceAccount": "${runtime_service_account}",
  "githubRepo": "${GITHUB_REPO}"
}
EOF
}

maybe_set_github_secret() {
    local name="$1" value="$2"
    if [[ -z "$GITHUB_REPO" || -z "$value" ]]; then
        return
    fi
    if ! command -v gh >/dev/null 2>&1; then
        GITHUB_WIRING_STATUS="manual_required"
        GITHUB_MANUAL_STEPS+=("gh secret set ${name} --repo ${GITHUB_REPO} --body '${value}'")
        return
    fi
    if ! gh auth status >/dev/null 2>&1; then
        GITHUB_WIRING_STATUS="manual_required"
        GITHUB_MANUAL_STEPS+=("gh secret set ${name} --repo ${GITHUB_REPO} --body '${value}'")
        return
    fi
    if ! gh secret set "$name" --repo "$GITHUB_REPO" --body "$value" >/dev/null 2>&1; then
        GITHUB_WIRING_STATUS="manual_required"
        GITHUB_MANUAL_STEPS+=("gh secret set ${name} --repo ${GITHUB_REPO} --body '${value}'")
        return
    fi
    if [[ "$GITHUB_WIRING_STATUS" != "manual_required" ]]; then
        GITHUB_WIRING_STATUS="configured"
    fi
}

maybe_set_github_variable() {
    local name="$1" value="$2"
    if [[ -z "$GITHUB_REPO" || -z "$value" ]]; then
        return
    fi
    if ! command -v gh >/dev/null 2>&1; then
        GITHUB_WIRING_STATUS="manual_required"
        GITHUB_MANUAL_STEPS+=("gh variable set ${name} --repo ${GITHUB_REPO} --body '${value}'")
        return
    fi
    if ! gh auth status >/dev/null 2>&1; then
        GITHUB_WIRING_STATUS="manual_required"
        GITHUB_MANUAL_STEPS+=("gh variable set ${name} --repo ${GITHUB_REPO} --body '${value}'")
        return
    fi
    if ! gh variable set "$name" --repo "$GITHUB_REPO" --body "$value" >/dev/null 2>&1; then
        GITHUB_WIRING_STATUS="manual_required"
        GITHUB_MANUAL_STEPS+=("gh variable set ${name} --repo ${GITHUB_REPO} --body '${value}'")
        return
    fi
    if [[ "$GITHUB_WIRING_STATUS" != "manual_required" ]]; then
        GITHUB_WIRING_STATUS="configured"
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --provider)
            PROVIDER="$2"
            shift 2
            ;;
        --instance-name)
            INSTANCE_NAME="$2"
            shift 2
            ;;
        --project-id)
            PROJECT_ID="$2"
            shift 2
            ;;
        --billing-account)
            BILLING_ACCOUNT="$2"
            shift 2
            ;;
        --region)
            REGION="$2"
            shift 2
            ;;
        --yes)
            YES="true"
            shift
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

if [[ "$PROVIDER" != "gcp" ]]; then
    echo "Only --provider gcp is currently implemented."
    exit 1
fi

require_cmd gcloud
require_cmd terraform
require_cmd docker
require_cmd openssl

if ! gcloud auth print-access-token >/dev/null 2>&1; then
    echo "gcloud auth is required. Run 'gcloud auth login' first."
    exit 1
fi

detect_github_repo

if [[ -z "$INSTANCE_NAME" ]]; then
    INSTANCE_NAME="$(sanitize_slug "logdate-${USER:-dev}-$(date +%y%m%d%H%M%S)")"
fi
if [[ -z "$PROJECT_ID" ]]; then
    PROJECT_ID="$(default_project_id)"
fi
validate_project_id "$PROJECT_ID"
if [[ -z "$STATE_BUCKET" ]]; then
    STATE_BUCKET="${PROJECT_ID}-tfstate"
fi
if [[ -z "$IMAGE_TAG" ]]; then
    if git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
        IMAGE_TAG="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
    else
        IMAGE_TAG="bootstrap"
    fi
fi

choose_billing_account

if ! gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1; then
    gcloud projects create "$PROJECT_ID" --name="$PROJECT_ID" >/dev/null
fi

gcloud_link_billing "$PROJECT_ID" "$BILLING_ACCOUNT"
gcloud config set project "$PROJECT_ID" >/dev/null
enable_bootstrap_apis "$PROJECT_ID"

if ! gcloud storage buckets describe "gs://${STATE_BUCKET}" >/dev/null 2>&1; then
    gcloud storage buckets create "gs://${STATE_BUCKET}" \
        --project="$PROJECT_ID" \
        --location="$REGION" \
        --uniform-bucket-level-access \
        --public-access-prevention >/dev/null
fi

INSTANCE_DIR="${REPO_ROOT}/.logdate/deploy/${PROJECT_ID}"
mkdir -p "$INSTANCE_DIR"
BACKEND_PATH="${INSTANCE_DIR}/backend.hcl"
TFVARS_PATH="${INSTANCE_DIR}/bootstrap.tfvars.json"
MANIFEST_PATH="${INSTANCE_DIR}/instance-manifest.json"

write_backend_config "$BACKEND_PATH"
write_tfvars "$TFVARS_PATH"

terraform -chdir="$TF_DIR" init -reconfigure -backend-config="$BACKEND_PATH" >/dev/null
terraform -chdir="$TF_DIR" apply -auto-approve -var-file="$TFVARS_PATH"
bootstrap_runtime_secrets

CONFIG_PATH="$TFVARS_PATH" \
CONFIG_MODE="full" \
GCP_PROJECT_ID="$PROJECT_ID" \
CLOUD_RUN_REGION="$REGION" \
CLOUD_RUN_SERVICE_NAME="$SERVICE_NAME" \
ARTIFACT_REGISTRY_REPO="$ARTIFACT_REGISTRY_REPO" \
IMAGE_TAG="$IMAGE_TAG" \
"$REPO_ROOT/scripts/deploy-cloud-run.sh"

SERVICE_URL="$(
    gcloud run services describe "$SERVICE_NAME" \
        --platform managed \
        --region "$REGION" \
        --project "$PROJECT_ID" \
        --format 'value(status.url)'
)"
SERVICE_HOST="${SERVICE_URL#https://}"

write_tfvars "$TFVARS_PATH" "$SERVICE_URL" "$SERVICE_HOST"

gcloud run services update "$SERVICE_NAME" \
    --platform managed \
    --region "$REGION" \
    --project "$PROJECT_ID" \
    --update-env-vars "LOGDATE_PUBLIC_ORIGIN=${SERVICE_URL},ATPROTO_PDS_SERVICE_URL=${SERVICE_URL},ATPROTO_HANDLE_DOMAIN=${SERVICE_HOST},WEBAUTHN_ORIGIN=${SERVICE_URL},WEBAUTHN_RP_ID=${SERVICE_HOST}" >/dev/null

WORKLOAD_IDENTITY_PROVIDER="$(terraform -chdir="$TF_DIR" output -raw workload_identity_provider 2>/dev/null || true)"
GITHUB_SERVICE_ACCOUNT="$(terraform -chdir="$TF_DIR" output -raw github_deploy_service_account_email 2>/dev/null || true)"
RUNTIME_SERVICE_ACCOUNT="$(terraform -chdir="$TF_DIR" output -raw runtime_service_account_email 2>/dev/null || true)"

maybe_set_github_secret "GCP_WORKLOAD_IDENTITY_PROVIDER" "$WORKLOAD_IDENTITY_PROVIDER"
maybe_set_github_secret "GCP_SERVICE_ACCOUNT" "$GITHUB_SERVICE_ACCOUNT"
maybe_set_github_variable "LOGDATE_DEPLOY_SOURCE" "$DEPLOY_SOURCE_MODE"
maybe_set_github_variable "LOGDATE_PROJECT_ID" "$PROJECT_ID"
maybe_set_github_variable "LOGDATE_REGION" "$REGION"
maybe_set_github_variable "LOGDATE_SERVICE_NAME" "$SERVICE_NAME"
maybe_set_github_variable "LOGDATE_ARTIFACT_REGISTRY_REPO" "$ARTIFACT_REGISTRY_REPO"
maybe_set_github_variable "LOGDATE_RUNTIME_SERVICE_ACCOUNT" "$RUNTIME_SERVICE_ACCOUNT"

write_manifest "$MANIFEST_PATH" "$SERVICE_URL" "$SERVICE_HOST" "$WORKLOAD_IDENTITY_PROVIDER" "$GITHUB_SERVICE_ACCOUNT" "$RUNTIME_SERVICE_ACCOUNT"

echo "Bootstrap complete."
echo "Project:      $PROJECT_ID"
echo "Region:       $REGION"
echo "Service URL:  $SERVICE_URL"
echo "Manifest:     $MANIFEST_PATH"
if [[ -n "$GITHUB_REPO" ]]; then
    echo "GitHub repo:  $GITHUB_REPO"
    if [[ "$GITHUB_WIRING_STATUS" == "configured" && -n "$WORKLOAD_IDENTITY_PROVIDER" && -n "$GITHUB_SERVICE_ACCOUNT" ]]; then
        echo "GitHub deploy identity is provisioned."
    elif [[ "$GITHUB_WIRING_STATUS" == "manual_required" ]]; then
        echo "GitHub deploy wiring needs manual follow-up:"
        printf '  %s\n' "${GITHUB_MANUAL_STEPS[@]}"
    fi
fi
