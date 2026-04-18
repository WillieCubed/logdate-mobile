#!/usr/bin/env bash
#
# deploy-production.sh
#
# One-command production deploy for the LogDate Ktor server. Intended for a
# Cloud Run instance that already has a GCP project, runtime service account,
# and Workload Identity Federation configured by `./scripts/setup-gcp-deploy.sh`
# (or `./scripts/bootstrap-gcp-fresh.sh` for a brand-new project).
#
# Phases (each is idempotent; skip with the matching flag):
#   0. Prereq validation
#   1. Domain ownership pre-flight (halts if unverified)
#   2. Write infra/terraform/production.tfvars (if missing or --overwrite)
#   3. terraform init + plan + apply
#   4. Populate Secret Manager values (auto-generates JWT secret; prompts for rest)
#   5. Build + push Docker image, deploy to Cloud Run
#   6. Emit the DNS resource records Cloud Run wants published
#   7. Verify /health on the service URL and each custom domain (waits for SSL)
#
# Usage:
#   ./scripts/deploy-production.sh --project-id <GCP_PROJECT_ID> [flags]
#
# Flags:
#   --project-id <id>            GCP project (required)
#   --region <region>            Default: us-central1
#   --primary-domain <host>      Default: logdate.hypertext.studio
#   --secondary-domain <host>    Default: cloud.logdate.app
#   --webauthn-rp-id <host>      Default: primary domain
#   --overwrite                  Overwrite an existing production.tfvars
#   --auto-approve               Skip the terraform apply confirmation prompt
#   --skip-prereqs               Skip Phase 0
#   --skip-domain-check          Skip Phase 1
#   --skip-tfvars                Skip Phase 2
#   --skip-terraform             Skip Phase 3
#   --skip-secrets               Skip Phase 4
#   --skip-image                 Skip Phase 5
#   --skip-dns                   Skip Phase 6
#   --skip-verify                Skip Phase 7
#   -h, --help                   Show this help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TF_DIR="${REPO_ROOT}/infra/terraform"
TFVARS_PATH="${TF_DIR}/production.tfvars"
BUILD_SCRIPT="${SCRIPT_DIR}/deploy-cloud-run.sh"

PROJECT_ID=""
REGION="us-central1"
PRIMARY_DOMAIN="logdate.hypertext.studio"
SECONDARY_DOMAIN="cloud.logdate.app"
WEBAUTHN_RP_ID=""
OVERWRITE_TFVARS="false"
AUTO_APPROVE="false"
SKIP_PREREQS="false"
SKIP_DOMAIN_CHECK="false"
SKIP_TFVARS="false"
SKIP_TERRAFORM="false"
SKIP_SECRETS="false"
SKIP_IMAGE="false"
SKIP_DNS="false"
SKIP_VERIFY="false"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log_phase() { printf '\n%b==> %s%b\n' "${CYAN}${BOLD}" "$1" "$NC"; }
log_info() { printf '%b[info]%b %s\n' "$GREEN" "$NC" "$1"; }
log_warn() { printf '%b[warn]%b %s\n' "$YELLOW" "$NC" "$1"; }
log_error() { printf '%b[error]%b %s\n' "$RED" "$NC" "$1" >&2; }

die() {
    log_error "$1"
    exit 1
}

print_usage() {
    sed -n '2,40p' "$0" | sed 's/^# \?//'
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --project-id) PROJECT_ID="$2"; shift 2 ;;
            --region) REGION="$2"; shift 2 ;;
            --primary-domain) PRIMARY_DOMAIN="$2"; shift 2 ;;
            --secondary-domain) SECONDARY_DOMAIN="$2"; shift 2 ;;
            --webauthn-rp-id) WEBAUTHN_RP_ID="$2"; shift 2 ;;
            --overwrite) OVERWRITE_TFVARS="true"; shift ;;
            --auto-approve) AUTO_APPROVE="true"; shift ;;
            --skip-prereqs) SKIP_PREREQS="true"; shift ;;
            --skip-domain-check) SKIP_DOMAIN_CHECK="true"; shift ;;
            --skip-tfvars) SKIP_TFVARS="true"; shift ;;
            --skip-terraform) SKIP_TERRAFORM="true"; shift ;;
            --skip-secrets) SKIP_SECRETS="true"; shift ;;
            --skip-image) SKIP_IMAGE="true"; shift ;;
            --skip-dns) SKIP_DNS="true"; shift ;;
            --skip-verify) SKIP_VERIFY="true"; shift ;;
            -h|--help) print_usage ;;
            *) die "Unknown flag: $1 (run $0 --help)" ;;
        esac
    done

    [[ -z "$PROJECT_ID" ]] && die "--project-id is required"
    [[ -z "$WEBAUTHN_RP_ID" ]] && WEBAUTHN_RP_ID="$PRIMARY_DOMAIN"
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

# ----------------------------------------------------------------------------
# Phase 0 — Prereq validation
# ----------------------------------------------------------------------------
phase_0_prereqs() {
    log_phase "Phase 0 — Prereq validation"

    require_cmd gcloud
    require_cmd terraform
    require_cmd docker
    require_cmd jq
    require_cmd openssl
    require_cmd curl
    log_info "CLI tools present"

    if ! gcloud auth list --filter=status:ACTIVE --format='value(account)' | grep -q '@'; then
        log_warn "No active gcloud credential — launching browser login"
        gcloud auth login
    fi
    log_info "gcloud authenticated as $(gcloud config get-value account 2>/dev/null)"

    gcloud config set project "$PROJECT_ID" >/dev/null
    log_info "Active project: $PROJECT_ID"

    local required_services=(
        run.googleapis.com
        artifactregistry.googleapis.com
        secretmanager.googleapis.com
        sqladmin.googleapis.com
        iam.googleapis.com
        iamcredentials.googleapis.com
        cloudresourcemanager.googleapis.com
        storage.googleapis.com
    )
    local enabled
    enabled=$(gcloud services list --enabled --project "$PROJECT_ID" --format='value(config.name)')
    local missing=()
    for svc in "${required_services[@]}"; do
        if ! grep -qx "$svc" <<<"$enabled"; then
            missing+=("$svc")
        fi
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
        log_info "Enabling services: ${missing[*]}"
        gcloud services enable "${missing[@]}" --project "$PROJECT_ID"
    fi
    log_info "All required GCP APIs are enabled"
}

# ----------------------------------------------------------------------------
# Phase 1 — Domain ownership pre-flight
# ----------------------------------------------------------------------------
phase_1_domain_check() {
    log_phase "Phase 1 — Domain ownership pre-flight"

    local verified
    verified=$(gcloud domains list-user-verified --format='value(id)' 2>/dev/null || true)

    local unverified=()
    for domain in "$PRIMARY_DOMAIN" "$SECONDARY_DOMAIN"; do
        if grep -qx "$domain" <<<"$verified"; then
            log_info "$domain — verified"
        else
            unverified+=("$domain")
        fi
    done

    if [[ ${#unverified[@]} -gt 0 ]]; then
        log_error "The following domains are not yet verified in Search Console:"
        for domain in "${unverified[@]}"; do
            printf '  • %s\n' "$domain"
            printf '    → %s\n' "https://search.google.com/search-console/welcome?resource_id=$domain"
        done
        cat <<'EOF'

Domain verification is a one-time browser flow. Steps:
  1. Open each URL above and choose "DNS record" verification.
  2. Publish the TXT record Google gives you at your DNS provider.
  3. Click "Verify" in Search Console; it usually takes under a minute.
  4. Rerun this script — or pass --skip-domain-check if verification is already in flight elsewhere.
EOF
        die "Halting until domains are verified"
    fi
}

# ----------------------------------------------------------------------------
# Phase 2 — Generate production.tfvars
# ----------------------------------------------------------------------------
phase_2_tfvars() {
    log_phase "Phase 2 — Generate $TFVARS_PATH"

    if [[ -f "$TFVARS_PATH" && "$OVERWRITE_TFVARS" != "true" ]]; then
        log_info "$TFVARS_PATH already exists; leaving unchanged (pass --overwrite to regenerate)"
        return
    fi

    local webauthn_origin="https://$WEBAUTHN_RP_ID"
    local allowed_origins="https://$PRIMARY_DOMAIN,https://$SECONDARY_DOMAIN"
    local github_repo
    github_repo=$(git -C "$REPO_ROOT" config --get remote.origin.url 2>/dev/null \
        | sed -E 's#(git@github\.com:|https?://github\.com/)##; s#\.git$##' \
        || true)

    cat >"$TFVARS_PATH" <<EOF
# Generated by scripts/deploy-production.sh on $(date -u +%Y-%m-%dT%H:%M:%SZ).
# Safe to commit; values here are non-sensitive configuration only. Secrets
# live in Secret Manager (see cloud_run_secret_env below).

project_id      = "$PROJECT_ID"
region          = "$REGION"
service_name    = "logdate-server"
cloud_run_image = "$REGION-docker.pkg.dev/$PROJECT_ID/logdate/logdate-server:latest"
webauthn_rp_id  = "$WEBAUTHN_RP_ID"
webauthn_origin = "$webauthn_origin"

enable_github_oidc = true
github_repo        = "$github_repo"

enable_domain_mapping = true
domains               = ["$PRIMARY_DOMAIN", "$SECONDARY_DOMAIN"]

create_gcs_bucket = true
gcs_bucket_name   = "logdate-media-$PROJECT_ID"

cloud_run_env = {
  LOGDATE_ENV     = "production"
  AUTO_MIGRATE    = "false"
  ALLOWED_ORIGINS = "$allowed_origins"
  REQUIRE_HTTPS   = "true"
}

cloud_run_secret_env = {
  DATABASE_URL           = { secret_id = "logdate-db-url" }
  DATABASE_USER          = { secret_id = "logdate-db-user" }
  DATABASE_PASSWORD      = { secret_id = "logdate-db-password" }
  JWT_SECRET             = { secret_id = "logdate-jwt-secret" }
  GOOGLE_OIDC_CLIENT_IDS = { secret_id = "logdate-google-oidc-client-ids" }
  REDIS_URL              = { secret_id = "logdate-redis-url" }
}

create_secrets = true
EOF

    log_info "Wrote $TFVARS_PATH"
    log_info "Remember to git-add and commit this file — it documents the live infra."
}

# ----------------------------------------------------------------------------
# Phase 3 — Terraform apply
# ----------------------------------------------------------------------------
phase_3_terraform() {
    log_phase "Phase 3 — terraform apply"

    [[ -f "$TFVARS_PATH" ]] || die "$TFVARS_PATH missing — run without --skip-tfvars first"

    (
        cd "$TF_DIR"
        terraform init -input=false
        terraform plan -input=false -var-file=production.tfvars -out=prod.tfplan

        if [[ "$AUTO_APPROVE" != "true" ]]; then
            read -rp "Apply the plan above? [y/N] " confirm
            [[ "$confirm" =~ ^[yY]$ ]] || die "Aborted by user"
        fi
        terraform apply -input=false prod.tfplan
        rm -f prod.tfplan
    )

    log_info "Terraform apply complete"
}

# ----------------------------------------------------------------------------
# Phase 4 — Populate Secret Manager
# ----------------------------------------------------------------------------
secret_has_version() {
    local secret_id="$1"
    gcloud secrets versions list "$secret_id" \
        --project "$PROJECT_ID" \
        --limit 1 \
        --format='value(name)' 2>/dev/null | grep -q .
}

put_secret_value() {
    local secret_id="$1"
    local value="$2"
    printf '%s' "$value" | gcloud secrets versions add "$secret_id" \
        --project "$PROJECT_ID" \
        --data-file=- >/dev/null
    log_info "  · wrote value for $secret_id"
}

prompt_secret() {
    local secret_id="$1"
    local label="$2"
    local is_password="$3"
    local allow_empty="${4:-false}"

    if secret_has_version "$secret_id"; then
        log_info "$secret_id already has a version — skipping"
        return
    fi

    local value
    if [[ "$is_password" == "true" ]]; then
        read -rsp "Enter $label (hidden): " value
        echo
    else
        read -rp "Enter $label: " value
    fi

    if [[ -z "$value" && "$allow_empty" != "true" ]]; then
        log_warn "$secret_id left unset — rerun with --skip-prereqs --skip-domain-check --skip-tfvars --skip-terraform --skip-image --skip-dns --skip-verify to prompt again"
        return
    fi
    put_secret_value "$secret_id" "$value"
}

phase_4_secrets() {
    log_phase "Phase 4 — Populate Secret Manager"

    # JWT secret: auto-generate if unset
    if secret_has_version logdate-jwt-secret; then
        log_info "logdate-jwt-secret already has a version — skipping"
    else
        log_info "Generating JWT secret via openssl rand -base64 48"
        local jwt
        jwt=$(openssl rand -base64 48 | tr -d '\n')
        put_secret_value logdate-jwt-secret "$jwt"
        unset jwt
    fi

    echo
    log_info "Database credentials — these must match the Cloud SQL user configured in Terraform."
    prompt_secret logdate-db-url "JDBC URL (e.g. jdbc:postgresql:///logdate?cloudSqlInstance=<instance>&socketFactory=com.google.cloud.sql.postgres.SocketFactory)" "false"
    prompt_secret logdate-db-user "database username" "false"
    prompt_secret logdate-db-password "database password" "true"

    echo
    log_info "Google OIDC client IDs — comma-separated, one per target platform (Android release, Android debug, iOS, Web)."
    prompt_secret logdate-google-oidc-client-ids "client IDs (CSV, blank to skip)" "false" "true"

    echo
    log_info "Redis URL — optional; leave blank to skip."
    prompt_secret logdate-redis-url "redis:// URL (blank to skip)" "false" "true"

    log_info "Secret Manager population complete"
}

# ----------------------------------------------------------------------------
# Phase 5 — Build image and deploy to Cloud Run
# ----------------------------------------------------------------------------
phase_5_image() {
    log_phase "Phase 5 — Build image and deploy"

    [[ -x "$BUILD_SCRIPT" ]] || die "Expected $BUILD_SCRIPT to be executable"

    ENVIRONMENT=production \
        CONFIG_MODE=image \
        CONFIG_PATH="$TFVARS_PATH" \
        "$BUILD_SCRIPT"

    log_info "Image built, pushed, and deployed"
}

# ----------------------------------------------------------------------------
# Phase 6 — Emit DNS records
# ----------------------------------------------------------------------------
print_records_for() {
    local domain="$1"
    local json
    json=$(gcloud beta run domain-mappings describe \
        --domain "$domain" \
        --region "$REGION" \
        --project "$PROJECT_ID" \
        --format=json 2>/dev/null || true)

    if [[ -z "$json" ]] || [[ "$json" == "null" ]]; then
        log_warn "No domain mapping found for $domain — Phase 3 may not have created it yet"
        return
    fi

    echo
    printf '%bDNS records for %s%b\n' "$BOLD" "$domain" "$NC"
    jq -r '.status.resourceRecords // [] | .[] | "  \(.type)  \(.name)  →  \(.rrdata)"' <<<"$json"
}

phase_6_dns() {
    log_phase "Phase 6 — DNS resource records to publish"

    print_records_for "$PRIMARY_DOMAIN"
    print_records_for "$SECONDARY_DOMAIN"

    cat <<EOF

Publish each record above at the respective DNS provider. Typical subdomains
resolve via a single CNAME to ghs.googlehosted.com; apex domains need four A
and four AAAA records. SSL provisioning begins once DNS is live and typically
completes within 15–30 minutes.
EOF
}

# ----------------------------------------------------------------------------
# Phase 7 — Verify
# ----------------------------------------------------------------------------
wait_health() {
    local url="$1"
    local label="$2"
    local deadline=$((SECONDS + 30 * 60))
    local code

    while (( SECONDS < deadline )); do
        if code=$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 10 "$url/health" 2>/dev/null); then
            if [[ "$code" == "200" ]]; then
                log_info "$label ($url) → 200 OK"
                return 0
            fi
        fi
        log_info "$label pending (last: ${code:-no response}); retrying in 60s"
        sleep 60
    done
    log_error "$label never returned 200 within 30 minutes"
    return 1
}

phase_7_verify() {
    log_phase "Phase 7 — Verify"

    local service_url
    service_url=$(gcloud run services describe logdate-server \
        --region "$REGION" \
        --project "$PROJECT_ID" \
        --format='value(status.url)')
    log_info "Cloud Run service URL: $service_url"

    wait_health "$service_url" "Cloud Run URL"
    wait_health "https://$PRIMARY_DOMAIN" "Primary domain"
    wait_health "https://$SECONDARY_DOMAIN" "Secondary domain"

    echo
    log_info "Deployment verified. Final checks:"
    printf '  • %s/health → 200\n' "$service_url"
    printf '  • https://%s/health → 200\n' "$PRIMARY_DOMAIN"
    printf '  • https://%s/health → 200\n' "$SECONDARY_DOMAIN"
}

# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------
main() {
    parse_args "$@"

    log_info "Project: $PROJECT_ID"
    log_info "Region: $REGION"
    log_info "Primary domain: $PRIMARY_DOMAIN"
    log_info "Secondary domain: $SECONDARY_DOMAIN"
    log_info "WebAuthn rpId: $WEBAUTHN_RP_ID"

    [[ "$SKIP_PREREQS" == "true" ]] || phase_0_prereqs
    [[ "$SKIP_DOMAIN_CHECK" == "true" ]] || phase_1_domain_check
    [[ "$SKIP_TFVARS" == "true" ]] || phase_2_tfvars
    [[ "$SKIP_TERRAFORM" == "true" ]] || phase_3_terraform
    [[ "$SKIP_SECRETS" == "true" ]] || phase_4_secrets
    [[ "$SKIP_IMAGE" == "true" ]] || phase_5_image
    [[ "$SKIP_DNS" == "true" ]] || phase_6_dns
    [[ "$SKIP_VERIFY" == "true" ]] || phase_7_verify

    log_phase "Done"
}

main "$@"
