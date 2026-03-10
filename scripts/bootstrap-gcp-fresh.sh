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
declare -a BILLING_ACCOUNT_IDS=()
declare -a BILLING_ACCOUNT_NAMES=()
declare -a ACCESSIBLE_PROJECT_IDS=()
declare -a ACCESSIBLE_PROJECT_NAMES=()
CURRENT_GCLOUD_PROJECT=""
PROJECT_EXISTS="unknown"
PROJECT_HAS_BILLING="unknown"
PROJECT_BILLING_ACCOUNT=""

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1"
        exit 1
    fi
}

log_step() {
    printf '\n%b==>%b %s\n' "$CYAN" "$NC" "$1"
}

log_info() {
    printf '%b[INFO]%b %s\n' "$GREEN" "$NC" "$1"
}

log_warn() {
    printf '%b[WARN]%b %s\n' "$YELLOW" "$NC" "$1"
}

log_error() {
    printf '%b[ERROR]%b %s\n' "$RED" "$NC" "$1"
}

print_usage() {
    cat <<EOF
Usage:
  ./scripts/bootstrap-gcp-fresh.sh [options]

Bootstrap a fresh first-party LogDate instance on GCP.

Options:
  --project-id ID         GCP project ID to create or reuse
  --billing-account ID    GCP billing account ID
  --region REGION         Cloud Run / Artifact Registry / Cloud SQL region
  --instance-name NAME    Human-friendly instance seed used for defaults
  --provider gcp          Only supported provider today
  --yes                   Headless mode; fail instead of prompting
  --help, -h              Show this help

Interactive mode:
  - Lets you select an existing GCP project or create a new one
  - Lists open billing accounts and lets you pick by number
  - Shows a config summary before provisioning starts
  - Lets you edit key values instead of typing everything upfront

Examples:
  ./scripts/bootstrap-gcp-fresh.sh
  ./scripts/bootstrap-gcp-fresh.sh --project-id my-logdate-prod --billing-account 000000-111111-222222
  ./scripts/bootstrap-gcp-fresh.sh --yes --project-id my-logdate-prod --billing-account 000000-111111-222222
EOF
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
        log_error "Invalid GCP project ID: $project_id"
        echo "Project IDs must be 6-30 chars, lowercase letters/digits/hyphens, start with a letter, and not end with a hyphen."
        exit 1
    fi
}

prompt_with_default() {
    local var_name="$1" prompt_text="$2" default_value="${3:-}"
    local input current_value
    current_value="${!var_name:-$default_value}"
    read -r -p "$prompt_text [${current_value}]: " input
    printf -v "$var_name" '%s' "${input:-$current_value}"
}

normalize_gcloud_value() {
    local value="$1"
    case "$value" in
        ""|"(unset)"|"None")
            printf ''
            ;;
        *)
            printf '%s' "$value"
            ;;
    esac
}

detect_current_gcloud_project() {
    CURRENT_GCLOUD_PROJECT="$(normalize_gcloud_value "$(gcloud config get-value project 2>/dev/null || true)")"
}

load_accessible_projects() {
    ACCESSIBLE_PROJECT_IDS=()
    ACCESSIBLE_PROJECT_NAMES=()

    local projects_output project_id project_name
    projects_output="$(gcloud projects list --limit=10 --format='value(projectId,name)' 2>/dev/null || true)"
    while IFS=$'\t' read -r project_id project_name; do
        [[ -z "$project_id" ]] && continue
        ACCESSIBLE_PROJECT_IDS+=("$project_id")
        ACCESSIBLE_PROJECT_NAMES+=("${project_name:-$project_id}")
    done <<< "$projects_output"

    if [[ -n "$CURRENT_GCLOUD_PROJECT" ]]; then
        local i found="false"
        for i in "${!ACCESSIBLE_PROJECT_IDS[@]}"; do
            if [[ "${ACCESSIBLE_PROJECT_IDS[$i]}" == "$CURRENT_GCLOUD_PROJECT" ]]; then
                found="true"
                break
            fi
        done
        if [[ "$found" == "false" ]]; then
            ACCESSIBLE_PROJECT_IDS=("$CURRENT_GCLOUD_PROJECT" "${ACCESSIBLE_PROJECT_IDS[@]}")
            ACCESSIBLE_PROJECT_NAMES=("Current gcloud project" "${ACCESSIBLE_PROJECT_NAMES[@]}")
        fi
    fi
}

describe_project() {
    local project_id="$1"
    local i suffix=""
    for i in "${!ACCESSIBLE_PROJECT_IDS[@]}"; do
        if [[ "${ACCESSIBLE_PROJECT_IDS[$i]}" == "$project_id" ]]; then
            if [[ "$project_id" == "$CURRENT_GCLOUD_PROJECT" && -n "$CURRENT_GCLOUD_PROJECT" ]]; then
                suffix=" [current]"
            fi
            printf '%s (%s)%s' "${ACCESSIBLE_PROJECT_NAMES[$i]}" "$project_id" "$suffix"
            return
        fi
    done
    if [[ "$project_id" == "$CURRENT_GCLOUD_PROJECT" && -n "$CURRENT_GCLOUD_PROJECT" ]]; then
        printf '%s [current]' "$project_id"
        return
    fi
    printf '%s' "$project_id"
}

refresh_project_context() {
    PROJECT_EXISTS="false"
    PROJECT_HAS_BILLING="false"
    PROJECT_BILLING_ACCOUNT=""

    [[ -z "$PROJECT_ID" ]] && return

    if gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1; then
        PROJECT_EXISTS="true"
        local billing_info billing_enabled billing_account_name
        billing_info="$(gcloud billing projects describe "$PROJECT_ID" --format='value(billingEnabled,billingAccountName)' 2>/dev/null || true)"
        read -r billing_enabled billing_account_name <<< "$billing_info"
        if [[ "$billing_enabled" == "True" || "$billing_enabled" == "true" || -n "$billing_account_name" ]]; then
            PROJECT_HAS_BILLING="true"
            PROJECT_BILLING_ACCOUNT="${billing_account_name#billingAccounts/}"
        fi
    fi
}

choose_project() {
    if [[ -n "$PROJECT_ID" ]]; then
        refresh_project_context
        return
    fi

    detect_current_gcloud_project
    if [[ "$YES" == "true" ]]; then
        if [[ -n "$CURRENT_GCLOUD_PROJECT" ]]; then
            PROJECT_ID="$CURRENT_GCLOUD_PROJECT"
            log_info "Using current gcloud project: $PROJECT_ID"
        else
            PROJECT_ID="$(default_project_id)"
            log_info "No current gcloud project found. Creating a new project: $PROJECT_ID"
        fi
        refresh_project_context
        return
    fi

    load_accessible_projects

    echo "Select a GCP project:"
    local option_count=0 i default_selection="" create_option manual_option
    for i in "${!ACCESSIBLE_PROJECT_IDS[@]}"; do
        option_count=$((option_count + 1))
        local marker=""
        if [[ "${ACCESSIBLE_PROJECT_IDS[$i]}" == "$CURRENT_GCLOUD_PROJECT" && -n "$CURRENT_GCLOUD_PROJECT" ]]; then
            marker=" [current]"
            default_selection="$option_count"
        fi
        printf '  %d) %s (%s)%s\n' "$option_count" "${ACCESSIBLE_PROJECT_NAMES[$i]}" "${ACCESSIBLE_PROJECT_IDS[$i]}" "$marker"
    done
    manual_option=$((option_count + 1))
    create_option=$((option_count + 2))
    echo "  ${manual_option}) Enter a different existing project ID"
    echo "  ${create_option}) Create a new project"

    if [[ -z "$default_selection" ]]; then
        if (( option_count > 0 )); then
            default_selection="1"
        else
            default_selection="$create_option"
        fi
    fi

    local selection
    while true; do
        read -r -p "Project [default ${default_selection}]: " selection
        selection="${selection:-$default_selection}"

        if [[ "$selection" =~ ^[0-9]+$ ]] && (( selection >= 1 && selection <= option_count )); then
            PROJECT_ID="${ACCESSIBLE_PROJECT_IDS[$((selection - 1))]}"
            refresh_project_context
            return
        fi

        if [[ "$selection" == "$manual_option" ]]; then
            prompt_with_default PROJECT_ID "Existing project ID" "${CURRENT_GCLOUD_PROJECT:-}"
            validate_project_id "$PROJECT_ID"
            refresh_project_context
            return
        fi

        if [[ "$selection" == "$create_option" ]]; then
            PROJECT_ID="$(default_project_id)"
            prompt_with_default PROJECT_ID "New project ID" "$PROJECT_ID"
            validate_project_id "$PROJECT_ID"
            PROJECT_EXISTS="false"
            PROJECT_HAS_BILLING="false"
            PROJECT_BILLING_ACCOUNT=""
            return
        fi

        echo "Enter one of the listed numbers."
    done
}

load_billing_accounts() {
    BILLING_ACCOUNT_IDS=()
    BILLING_ACCOUNT_NAMES=()

    local accounts_output line full_id display_name account_id
    accounts_output="$(gcloud billing accounts list --filter='open=true' --format='value(name,displayName)' 2>/dev/null || true)"
    while IFS=$'\t' read -r full_id display_name; do
        [[ -z "$full_id" ]] && continue
        account_id="${full_id#billingAccounts/}"
        BILLING_ACCOUNT_IDS+=("$account_id")
        BILLING_ACCOUNT_NAMES+=("${display_name:-Unnamed billing account}")
    done <<< "$accounts_output"
}

print_billing_account_options() {
    local i
    echo "Open billing accounts:"
    for i in "${!BILLING_ACCOUNT_IDS[@]}"; do
        printf '  %d) %s (%s)\n' "$((i + 1))" "${BILLING_ACCOUNT_NAMES[$i]}" "${BILLING_ACCOUNT_IDS[$i]}"
    done
}

describe_billing_account() {
    local account_id="$1"
    local i
    for i in "${!BILLING_ACCOUNT_IDS[@]}"; do
        if [[ "${BILLING_ACCOUNT_IDS[$i]}" == "$account_id" ]]; then
            printf '%s (%s)' "${BILLING_ACCOUNT_NAMES[$i]}" "$account_id"
            return
        fi
    done
    printf '%s' "$account_id"
}

choose_region() {
    local common_regions=(
        "us-central1"
        "us-east1"
        "us-west1"
        "us-west2"
        "europe-west1"
    )

    if [[ "$YES" == "true" ]]; then
        return
    fi

    echo "Select a region:"
    local i
    for i in "${!common_regions[@]}"; do
        local marker=""
        if [[ "${common_regions[$i]}" == "$REGION" ]]; then
            marker=" (Current)"
        fi
        printf '  %d) %s%s\n' "$((i + 1))" "${common_regions[$i]}" "$marker"
    done
    echo "  6) Enter a custom region"

    local selection
    while true; do
        read -r -p "Region [1-6]: " selection
        case "${selection:-1}" in
            1|2|3|4|5)
                REGION="${common_regions[$((selection - 1))]}"
                return
                ;;
            6)
                prompt_with_default REGION "Custom region" "$REGION"
                return
                ;;
            *)
                echo "Enter 1-6."
                ;;
        esac
    done
}

show_configuration() {
    load_billing_accounts
    refresh_project_context
    echo ""
    echo "Bootstrap configuration:"
    printf '  1) Project:             %s\n' "$(describe_project "$PROJECT_ID")"
    if [[ "$PROJECT_EXISTS" == "true" ]]; then
        printf '     Project mode:        Reuse existing project\n'
    else
        printf '     Project mode:        Create new project\n'
    fi
    if [[ "$PROJECT_HAS_BILLING" == "true" ]]; then
        printf '  2) Billing account:     Reuse existing project billing (%s)\n' "$(describe_billing_account "${PROJECT_BILLING_ACCOUNT:-$BILLING_ACCOUNT}")"
    else
        printf '  2) Billing account:     %s\n' "$(describe_billing_account "$BILLING_ACCOUNT")"
    fi
    printf '  3) Region:              %s\n' "$REGION"
    printf '  4) Service name:        %s\n' "$SERVICE_NAME"
    printf '  5) Artifact repo:       %s\n' "$ARTIFACT_REGISTRY_REPO"
    printf '  6) GitHub repo:         %s\n' "${GITHUB_REPO:-Not configured}"
    printf '  7) State bucket:        %s\n' "${STATE_BUCKET}"
    printf '  8) Cloud SQL instance:  %s\n' "${CLOUD_SQL_INSTANCE_NAME}"
}

review_configuration() {
    if [[ "$YES" == "true" ]]; then
        return
    fi

    while true; do
        show_configuration
        echo ""
        read -r -p "Proceed with this configuration? [Y/e/n] " confirm
        case "${confirm:-y}" in
            y|Y)
                return
                ;;
            e|E)
                read -r -p "Edit field [1-8]: " field
                case "$field" in
                    1)
                        PROJECT_ID=""
                        choose_project
                        if [[ -z "${TF_STATE_BUCKET:-}" ]]; then
                            STATE_BUCKET="${PROJECT_ID}-tfstate"
                        fi
                        ;;
                    2)
                        choose_billing_account force
                        ;;
                    3)
                        choose_region
                        ;;
                    4)
                        prompt_with_default SERVICE_NAME "Service name" "$SERVICE_NAME"
                        ;;
                    5)
                        prompt_with_default ARTIFACT_REGISTRY_REPO "Artifact Registry repo" "$ARTIFACT_REGISTRY_REPO"
                        ;;
                    6)
                        prompt_with_default GITHUB_REPO "GitHub repo (owner/repo)" "${GITHUB_REPO:-}"
                        ;;
                    7)
                        prompt_with_default STATE_BUCKET "Terraform state bucket" "$STATE_BUCKET"
                        ;;
                    8)
                        prompt_with_default CLOUD_SQL_INSTANCE_NAME "Cloud SQL instance name" "$CLOUD_SQL_INSTANCE_NAME"
                        ;;
                    *)
                        echo "Enter 1-8."
                        ;;
                esac
                ;;
            n|N)
                echo "Aborted."
                exit 1
                ;;
            *)
                echo "Enter y, e, or n."
                ;;
        esac
    done
}

choose_billing_account() {
    if [[ -n "$BILLING_ACCOUNT" && "${1:-}" != "force" ]]; then
        return
    fi

    load_billing_accounts
    local count="${#BILLING_ACCOUNT_IDS[@]}"

    if [[ "$count" == "0" ]]; then
        log_error "No open billing accounts were found for your gcloud identity."
        echo "Run 'gcloud billing accounts list --format=\"table(name,displayName,open)\"' to inspect available accounts."
        echo "If you know the account ID already, rerun with --billing-account BILLING_ACCOUNT_ID."
        exit 1
    fi

    if [[ "$count" == "1" ]]; then
        BILLING_ACCOUNT="${BILLING_ACCOUNT_IDS[0]}"
        log_info "Using billing account: $(describe_billing_account "$BILLING_ACCOUNT")"
        return
    fi

    if [[ "$YES" == "true" ]]; then
        log_error "Multiple open billing accounts found."
        print_billing_account_options
        echo "Pass --billing-account in headless mode."
        exit 1
    fi

    print_billing_account_options
    echo "Choose a number or paste an exact billing account ID."

    local selection i
    while true; do
        read -r -p "Billing account [1-${count}]: " selection
        selection="${selection:-1}"
        if [[ "$selection" =~ ^[0-9]+$ ]] && (( selection >= 1 && selection <= count )); then
            BILLING_ACCOUNT="${BILLING_ACCOUNT_IDS[$((selection - 1))]}"
            return
        fi

        for i in "${!BILLING_ACCOUNT_IDS[@]}"; do
            if [[ "${BILLING_ACCOUNT_IDS[$i]}" == "${selection#billingAccounts/}" ]]; then
                BILLING_ACCOUNT="${BILLING_ACCOUNT_IDS[$i]}"
                return
            fi
        done

        echo "Enter a number from the list or an exact billing account ID."
    done
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
        --help|-h)
            print_usage
            exit 0
            ;;
        *)
            log_error "Unknown argument: $1"
            print_usage
            exit 1
            ;;
    esac
done

if [[ "$PROVIDER" != "gcp" ]]; then
    log_error "Only --provider gcp is currently implemented."
    exit 1
fi

require_cmd gcloud
require_cmd terraform
require_cmd docker
require_cmd openssl

if ! gcloud auth print-access-token >/dev/null 2>&1; then
    log_error "gcloud auth is required. Run 'gcloud auth login' first."
    exit 1
fi

detect_github_repo

choose_project
if [[ -z "$INSTANCE_NAME" ]]; then
    INSTANCE_NAME="$(sanitize_slug "logdate-${PROJECT_ID:-${USER:-dev}}")"
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

if [[ "$PROJECT_EXISTS" == "true" && "$PROJECT_HAS_BILLING" == "true" ]]; then
    BILLING_ACCOUNT="${PROJECT_BILLING_ACCOUNT:-$BILLING_ACCOUNT}"
    load_billing_accounts
    log_info "Reusing billing already attached to project: $(describe_billing_account "$BILLING_ACCOUNT")"
else
    choose_billing_account
fi
review_configuration

log_step "Preparing GCP project"
if ! gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1; then
    gcloud projects create "$PROJECT_ID" --name="$PROJECT_ID" >/dev/null
fi

if [[ "$PROJECT_EXISTS" == "true" && "$PROJECT_HAS_BILLING" == "true" ]]; then
    log_info "Keeping existing billing attachment on project."
else
    gcloud_link_billing "$PROJECT_ID" "$BILLING_ACCOUNT"
fi
gcloud config set project "$PROJECT_ID" >/dev/null
enable_bootstrap_apis "$PROJECT_ID"

log_step "Preparing Terraform state"
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

log_step "Provisioning infrastructure"
terraform -chdir="$TF_DIR" init -reconfigure -backend-config="$BACKEND_PATH" >/dev/null
terraform -chdir="$TF_DIR" apply -auto-approve -var-file="$TFVARS_PATH"

log_step "Uploading runtime secrets"
bootstrap_runtime_secrets

log_step "Deploying Cloud Run service"
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

log_step "Configuring GitHub deploy metadata"
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
