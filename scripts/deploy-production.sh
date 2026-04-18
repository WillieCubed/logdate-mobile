#!/usr/bin/env bash
#
# deploy-production.sh — Interactive turnkey production deploy for the LogDate
# Ktor server on GCP Cloud Run.
#
# Defaults to zero-flag interactive mode: run `./run deploy:production` with no
# arguments and the script walks you through every missing input. Every
# interactive prompt can be pre-answered with an env var or a CLI flag for
# unattended reruns.
#
# Phases (each idempotent, each skippable via --skip-<phase>):
#   0. Prereq validation + gcloud auth
#   1. Domain ownership pre-flight (Search Console verify URLs)
#   2. Write infra/terraform/production.tfvars (if missing or --overwrite)
#   3. terraform init + plan + apply
#   4. Populate Secret Manager (auto-generates JWT; prompts for DB/OIDC/Redis)
#   5. Build + push Docker image, deploy to Cloud Run
#   6. Publish DNS records (via Cloudflare API) or print them for manual entry
#   7. Verify /health on the service URL and each custom domain
#
# Usage (all flags optional — script prompts for missing values):
#   ./scripts/deploy-production.sh [flags]
#
# Flags:
#   --project-id <id>            GCP project (default: auto-detect)
#   --region <region>            Default: us-central1
#   --primary-domain <host>      Default: logdate.hypertext.studio
#   --secondary-domain <host>    Default: cloud.logdate.app (empty string to skip)
#   --webauthn-rp-id <host>      Default: primary domain
#   --dns-provider <prov>        cloudflare | manual  (default: prompt)
#   --overwrite                  Overwrite existing production.tfvars
#   --auto-approve               Skip terraform apply confirmation
#   --non-interactive            Fail instead of prompting for missing inputs
#   --skip-prereqs, --skip-domain-check, --skip-tfvars, --skip-terraform,
#   --skip-secrets, --skip-image, --skip-dns, --skip-verify
#   -h, --help
#
# Env vars (all optional; every one can also be set via a flag or prompt):
#   LOGDATE_GCP_PROJECT_ID       GCP project
#   CLOUDFLARE_API_TOKEN         Cloudflare API token with Zone:DNS:Edit

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TF_DIR="${REPO_ROOT}/infra/terraform"
TFVARS_PATH="${TF_DIR}/production.tfvars"
BUILD_SCRIPT="${SCRIPT_DIR}/deploy-cloud-run.sh"

PROJECT_ID="${LOGDATE_GCP_PROJECT_ID:-}"
REGION="us-central1"
PRIMARY_DOMAIN="logdate.hypertext.studio"
SECONDARY_DOMAIN="cloud.logdate.app"
WEBAUTHN_RP_ID=""
DNS_PROVIDER=""
OVERWRITE_TFVARS="false"
AUTO_APPROVE="false"
NON_INTERACTIVE="false"
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
    sed -n '2,43p' "$0" | sed 's/^# \?//'
    exit 0
}

prompt_or_die() {
    local prompt="$1"
    local default="${2:-}"
    local out
    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        [[ -n "$default" ]] && { echo "$default"; return; }
        die "Missing required input ($prompt) in --non-interactive mode"
    fi
    if [[ -n "$default" ]]; then
        read -rp "$prompt [$default]: " out
        echo "${out:-$default}"
    else
        read -rp "$prompt: " out
        echo "$out"
    fi
}

prompt_secret_value() {
    local prompt="$1"
    local out
    read -rsp "$prompt: " out
    echo >&2
    echo "$out"
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --project-id) PROJECT_ID="$2"; shift 2 ;;
            --region) REGION="$2"; shift 2 ;;
            --primary-domain) PRIMARY_DOMAIN="$2"; shift 2 ;;
            --secondary-domain) SECONDARY_DOMAIN="$2"; shift 2 ;;
            --webauthn-rp-id) WEBAUTHN_RP_ID="$2"; shift 2 ;;
            --dns-provider) DNS_PROVIDER="$2"; shift 2 ;;
            --overwrite) OVERWRITE_TFVARS="true"; shift ;;
            --auto-approve) AUTO_APPROVE="true"; shift ;;
            --non-interactive) NON_INTERACTIVE="true"; shift ;;
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
}

# ----------------------------------------------------------------------------
# Auto-detection helpers
# ----------------------------------------------------------------------------

# Read `project_id = "foo"` out of an existing tfvars file.
read_tfvars_project_id() {
    local file="$1"
    [[ -f "$file" ]] || return 1
    awk -F'=' '/^[[:space:]]*project_id[[:space:]]*=/ {
        gsub(/[[:space:]"]/, "", $2); print $2; exit
    }' "$file"
}

detect_project_id() {
    if [[ -n "$PROJECT_ID" ]]; then
        log_info "Using project id from flag/env: $PROJECT_ID"
        return
    fi

    local candidates=()

    local committed
    committed=$(read_tfvars_project_id "$TFVARS_PATH" 2>/dev/null || true)
    [[ -n "$committed" ]] && candidates+=("$committed (infra/terraform/production.tfvars)")

    if [[ -d "${REPO_ROOT}/.logdate/deploy" ]]; then
        local bootstrap_dir
        while IFS= read -r bootstrap_dir; do
            local id
            id=$(basename "$bootstrap_dir")
            [[ -n "$id" ]] && candidates+=("$id (.logdate/deploy/$id)")
        done < <(find "${REPO_ROOT}/.logdate/deploy" -mindepth 1 -maxdepth 1 -type d 2>/dev/null || true)
    fi

    local active
    active=$(gcloud config get-value project 2>/dev/null || true)
    [[ -n "$active" && "$active" != "(unset)" ]] && candidates+=("$active (gcloud active project)")

    if [[ ${#candidates[@]} -eq 0 ]]; then
        PROJECT_ID=$(prompt_or_die "GCP project ID")
        [[ -z "$PROJECT_ID" ]] && die "Project ID is required"
        return
    fi

    if [[ ${#candidates[@]} -eq 1 ]]; then
        local only="${candidates[0]}"
        PROJECT_ID="${only%% *}"
        log_info "Detected project id: $only"
        return
    fi

    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        log_error "Multiple project candidates found:"
        printf '  • %s\n' "${candidates[@]}"
        die "Pass --project-id to disambiguate in non-interactive mode"
    fi

    echo "Multiple project candidates found:"
    local i=1
    for c in "${candidates[@]}"; do
        printf '  %d) %s\n' "$i" "$c"
        ((i++))
    done
    local choice
    read -rp "Select [1]: " choice
    choice="${choice:-1}"
    [[ "$choice" =~ ^[0-9]+$ ]] || die "Invalid selection: $choice"
    (( choice >= 1 && choice <= ${#candidates[@]} )) || die "Selection out of range"
    local picked="${candidates[$((choice - 1))]}"
    PROJECT_ID="${picked%% *}"
    log_info "Using $PROJECT_ID"
}

detect_dns_provider() {
    if [[ -n "$DNS_PROVIDER" ]]; then
        return
    fi

    if [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]]; then
        DNS_PROVIDER="cloudflare"
        log_info "CLOUDFLARE_API_TOKEN present → DNS provider: cloudflare"
        return
    fi

    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        DNS_PROVIDER="manual"
        return
    fi

    echo
    echo "Pick a DNS provider for publishing the Cloud Run mapping records:"
    echo "  1) manual      — script prints records for you to copy into your registrar"
    echo "  2) cloudflare  — script publishes records via the Cloudflare API (needs CLOUDFLARE_API_TOKEN)"
    local choice
    read -rp "Select [1]: " choice
    case "${choice:-1}" in
        1) DNS_PROVIDER="manual" ;;
        2) DNS_PROVIDER="cloudflare" ;;
        *) die "Invalid DNS provider choice: $choice" ;;
    esac
}

# ----------------------------------------------------------------------------
# gcloud identity confirmation — runs before any mutating action so the user
# can catch a wrong-account mistake before resources are touched.
# ----------------------------------------------------------------------------
confirm_gcloud_identity() {
    log_phase "Confirming gcloud identity"

    local required=(gcloud terraform docker jq openssl curl)
    for cmd in "${required[@]}"; do
        command -v "$cmd" >/dev/null 2>&1 || die "Missing required command: $cmd"
    done

    local accounts
    accounts=$(gcloud auth list --format='value(account)' 2>/dev/null || true)
    if [[ -z "$accounts" ]]; then
        if [[ "$NON_INTERACTIVE" == "true" ]]; then
            die "No gcloud accounts configured — run 'gcloud auth login' first"
        fi
        log_warn "No gcloud accounts configured — launching browser login"
        gcloud auth login
        accounts=$(gcloud auth list --format='value(account)' 2>/dev/null || true)
    fi

    local active
    active=$(gcloud config get-value account 2>/dev/null || true)
    if [[ -z "$active" || "$active" == "(unset)" ]]; then
        die "gcloud has accounts configured but none is active. Run: gcloud config set account <email>"
    fi

    local current_project
    current_project=$(gcloud config get-value project 2>/dev/null || true)
    [[ "$current_project" == "(unset)" ]] && current_project=""

    echo
    printf '  %bAccount:%b %s\n' "$BOLD" "$NC" "$active"
    printf '  %bProject:%b %s\n' "$BOLD" "$NC" "${current_project:-<none>}"
    echo

    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        log_info "Proceeding with $active (non-interactive)"
        return
    fi

    local confirm
    read -rp "Is this the account you want to deploy with? [Y/n] " confirm
    if [[ ! "$confirm" =~ ^[nN]$ ]]; then
        return
    fi

    echo "Available accounts:"
    local -a ids=()
    local i=1
    while IFS= read -r acct; do
        [[ -z "$acct" ]] && continue
        printf '  %d) %s\n' "$i" "$acct"
        ids+=("$acct")
        ((i++))
    done <<<"$accounts"
    printf '  %d) Log in as a different account\n' "$i"

    local choice
    read -rp "Select [1]: " choice
    choice="${choice:-1}"
    [[ "$choice" =~ ^[0-9]+$ ]] || die "Invalid selection: $choice"

    if (( choice == i )); then
        gcloud auth login
        active=$(gcloud config get-value account 2>/dev/null || true)
    elif (( choice >= 1 && choice < i )); then
        active="${ids[$((choice - 1))]}"
        gcloud config set account "$active"
    else
        die "Selection out of range"
    fi
    log_info "Now using gcloud account: $active"
}

# ----------------------------------------------------------------------------
# Phase 0 — Prereq validation
# ----------------------------------------------------------------------------
phase_0_prereqs() {
    log_phase "Phase 0 — Prereq validation"

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
        grep -qx "$svc" <<<"$enabled" || missing+=("$svc")
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

    local targets=("$PRIMARY_DOMAIN")
    [[ -n "$SECONDARY_DOMAIN" ]] && targets+=("$SECONDARY_DOMAIN")

    local unverified=()
    for domain in "${targets[@]}"; do
        if grep -qx "$domain" <<<"$verified"; then
            log_info "$domain — verified"
        else
            unverified+=("$domain")
        fi
    done

    if [[ ${#unverified[@]} -eq 0 ]]; then
        return
    fi

    log_warn "The following domains are not yet verified:"
    for domain in "${unverified[@]}"; do
        printf '  • %s\n' "$domain"
    done

    cat <<'EOF'

Domain ownership is verified through Google Site Verification (the system
Search Console uses). The easiest path is `gcloud domains verify`, which
opens the correct browser flow for each domain:

  gcloud domains verify <domain>

Publish the TXT record Google shows, click "Verify", then return here.
(If your DNS provider is Cloudflare and you've set CLOUDFLARE_API_TOKEN,
you can paste the TXT value in the Cloudflare dashboard; publishing
verification records via the Cloudflare API isn't wired up yet.)
EOF

    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        die "Halting until domains are verified"
    fi

    local do_verify
    read -rp "Launch 'gcloud domains verify' for each unverified domain now? [Y/n] " do_verify
    if [[ ! "$do_verify" =~ ^[nN]$ ]]; then
        for domain in "${unverified[@]}"; do
            log_info "Opening verification flow for $domain"
            gcloud domains verify "$domain" || log_warn "gcloud domains verify $domain exited non-zero; verify manually"
        done
    fi

    read -rp "Press Enter once every domain above shows as verified (or Ctrl-C to abort) " _
    verified=$(gcloud domains list-user-verified --format='value(id)' 2>/dev/null || true)
    for domain in "${unverified[@]}"; do
        grep -qx "$domain" <<<"$verified" || die "$domain still not verified — rerun once it is"
    done
    log_info "All target domains verified"
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

    [[ -z "$WEBAUTHN_RP_ID" ]] && WEBAUTHN_RP_ID="$PRIMARY_DOMAIN"
    local webauthn_origin="https://$WEBAUTHN_RP_ID"

    local allowed_origins="https://$PRIMARY_DOMAIN"
    local domains_list="[\"$PRIMARY_DOMAIN\""
    if [[ -n "$SECONDARY_DOMAIN" ]]; then
        allowed_origins="$allowed_origins,https://$SECONDARY_DOMAIN"
        domains_list="$domains_list, \"$SECONDARY_DOMAIN\""
    fi
    domains_list="$domains_list]"

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
domains               = $domains_list

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
            if [[ "$NON_INTERACTIVE" == "true" ]]; then
                die "Refusing to apply without --auto-approve in --non-interactive mode"
            fi
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
    gcloud secrets versions list "$1" \
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
    log_info "  · wrote version for $secret_id"
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
    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        log_warn "$secret_id missing a version; pass it via gcloud secrets or rerun interactively"
        return
    fi

    if [[ "$is_password" == "true" ]]; then
        value=$(prompt_secret_value "Enter $label (hidden)")
    else
        read -rp "Enter $label: " value
    fi

    if [[ -z "$value" && "$allow_empty" != "true" ]]; then
        log_warn "$secret_id left unset — rerun with --skip-* flags to prompt again"
        return
    fi
    put_secret_value "$secret_id" "$value"
}

phase_4_secrets() {
    log_phase "Phase 4 — Populate Secret Manager"

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
    log_info "Database credentials — must match the Cloud SQL user configured by Terraform."
    prompt_secret logdate-db-url "JDBC URL (jdbc:postgresql:///logdate?cloudSqlInstance=...&socketFactory=com.google.cloud.sql.postgres.SocketFactory)" "false"
    prompt_secret logdate-db-user "database username" "false"
    prompt_secret logdate-db-password "database password" "true"

    echo
    log_info "Google OIDC client IDs — comma-separated, one per platform (Android release, Android debug, iOS, Web)."
    prompt_secret logdate-google-oidc-client-ids "client IDs (CSV, blank to skip)" "false" "true"

    echo
    log_info "Redis URL — optional; leave blank to skip."
    prompt_secret logdate-redis-url "redis:// URL (blank to skip)" "false" "true"

    log_info "Secret Manager population complete"
}

# ----------------------------------------------------------------------------
# Phase 5 — Build image and deploy
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
# Phase 6 — DNS records
# ----------------------------------------------------------------------------

# Fetch the resourceRecords Cloud Run wants for a given domain.
cloud_run_records_json() {
    gcloud beta run domain-mappings describe \
        --domain "$1" \
        --region "$REGION" \
        --project "$PROJECT_ID" \
        --format=json 2>/dev/null || echo ''
}

print_manual_records_for() {
    local domain="$1"
    local json
    json=$(cloud_run_records_json "$domain")
    if [[ -z "$json" || "$json" == "null" ]]; then
        log_warn "No domain mapping found for $domain — Phase 3 may not have created it yet"
        return
    fi
    echo
    printf '%bDNS records for %s%b\n' "$BOLD" "$domain" "$NC"
    jq -r '.status.resourceRecords // [] | .[] | "  \(.type)  \(.name)  →  \(.rrdata)"' <<<"$json"
}

# Resolve the Cloudflare zone id whose name is a suffix of `$domain`.
cloudflare_zone_id_for() {
    local domain="$1"
    local zones
    zones=$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
        "https://api.cloudflare.com/client/v4/zones?per_page=50" 2>/dev/null || echo '')
    [[ -z "$zones" ]] && { log_error "Failed to list Cloudflare zones (token or network)"; return 1; }
    jq -r --arg domain "$domain" '
        .result // [] |
        map(select($domain | endswith(.name))) |
        sort_by(.name | length) | reverse | .[0].id // empty
    ' <<<"$zones"
}

# Check if a record already exists at (zone, type, name). Echoes the record id.
cloudflare_find_record() {
    local zone_id="$1" rtype="$2" rname="$3"
    curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
        "https://api.cloudflare.com/client/v4/zones/$zone_id/dns_records?type=$rtype&name=$rname" 2>/dev/null \
        | jq -r '.result // [] | .[0].id // empty'
}

# Upsert a single DNS record at Cloudflare. Returns 0 on success.
cloudflare_upsert_record() {
    local zone_id="$1" rtype="$2" rname="$3" rcontent="$4"
    # Strip any trailing dot Google returns for the rrdata.
    rcontent="${rcontent%.}"
    rname="${rname%.}"

    local existing
    existing=$(cloudflare_find_record "$zone_id" "$rtype" "$rname")
    local body
    body=$(jq -n \
        --arg type "$rtype" \
        --arg name "$rname" \
        --arg content "$rcontent" \
        '{type:$type, name:$name, content:$content, ttl:300, proxied:false}')

    local url method
    if [[ -n "$existing" ]]; then
        url="https://api.cloudflare.com/client/v4/zones/$zone_id/dns_records/$existing"
        method="PUT"
    else
        url="https://api.cloudflare.com/client/v4/zones/$zone_id/dns_records"
        method="POST"
    fi

    local resp
    resp=$(curl -fsS -X "$method" \
        -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
        -H "Content-Type: application/json" \
        --data "$body" \
        "$url" 2>/dev/null || echo '')
    [[ -z "$resp" ]] && { log_error "Cloudflare $method failed for $rtype $rname"; return 1; }
    if [[ $(jq -r '.success // false' <<<"$resp") != "true" ]]; then
        log_error "Cloudflare rejected $rtype $rname → $rcontent"
        jq -r '.errors // [] | .[] | "  · \(.message)"' <<<"$resp" >&2
        return 1
    fi
    log_info "  · $rtype $rname → $rcontent  ($(echo "$method" | tr '[:upper:]' '[:lower:]'))"
}

publish_cloudflare_records_for() {
    local domain="$1"
    local json
    json=$(cloud_run_records_json "$domain")
    if [[ -z "$json" || "$json" == "null" ]]; then
        log_warn "No domain mapping found for $domain — Phase 3 may not have created it yet"
        return
    fi
    local zone_id
    zone_id=$(cloudflare_zone_id_for "$domain" || true)
    if [[ -z "$zone_id" ]]; then
        log_warn "No Cloudflare zone found for $domain — falling back to manual output"
        print_manual_records_for "$domain"
        return
    fi
    log_info "Publishing $domain records to Cloudflare zone $zone_id"
    local count
    count=$(jq '.status.resourceRecords // [] | length' <<<"$json")
    if [[ "$count" == "0" ]]; then
        log_warn "Cloud Run emitted zero records for $domain; nothing to publish"
        return
    fi
    while IFS=$'\t' read -r rtype rname rcontent; do
        cloudflare_upsert_record "$zone_id" "$rtype" "$rname" "$rcontent" || true
    done < <(jq -r '.status.resourceRecords // [] | .[] | [.type, .name, .rrdata] | @tsv' <<<"$json")
}

phase_6_dns() {
    log_phase "Phase 6 — DNS records"

    local targets=("$PRIMARY_DOMAIN")
    [[ -n "$SECONDARY_DOMAIN" ]] && targets+=("$SECONDARY_DOMAIN")

    case "$DNS_PROVIDER" in
        cloudflare)
            [[ -n "${CLOUDFLARE_API_TOKEN:-}" ]] || {
                if [[ "$NON_INTERACTIVE" == "true" ]]; then
                    die "CLOUDFLARE_API_TOKEN env var is required for --dns-provider cloudflare"
                fi
                CLOUDFLARE_API_TOKEN=$(prompt_secret_value "Enter CLOUDFLARE_API_TOKEN (Zone:DNS:Edit)")
                export CLOUDFLARE_API_TOKEN
            }
            for domain in "${targets[@]}"; do
                publish_cloudflare_records_for "$domain"
            done
            ;;
        manual|"")
            for domain in "${targets[@]}"; do
                print_manual_records_for "$domain"
            done
            cat <<EOF

Publish each record above at the respective DNS provider. Typical subdomain
mappings resolve via a single CNAME to ghs.googlehosted.com; apex domains
need four A and four AAAA records. SSL provisioning begins once DNS is live
and typically completes within 15–30 minutes.
EOF
            ;;
        *)
            die "Unsupported DNS provider: $DNS_PROVIDER (supported: cloudflare, manual)"
            ;;
    esac
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
    [[ -n "$SECONDARY_DOMAIN" ]] && wait_health "https://$SECONDARY_DOMAIN" "Secondary domain"

    echo
    log_info "Deployment verified."
    printf '  • %s/health → 200\n' "$service_url"
    printf '  • https://%s/health → 200\n' "$PRIMARY_DOMAIN"
    [[ -n "$SECONDARY_DOMAIN" ]] && printf '  • https://%s/health → 200\n' "$SECONDARY_DOMAIN"
}

# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------
main() {
    parse_args "$@"

    confirm_gcloud_identity
    detect_project_id
    detect_dns_provider

    log_info "Project: $PROJECT_ID"
    log_info "Region: $REGION"
    log_info "Primary domain: $PRIMARY_DOMAIN"
    log_info "Secondary domain: ${SECONDARY_DOMAIN:-<none>}"
    log_info "WebAuthn rpId: ${WEBAUTHN_RP_ID:-$PRIMARY_DOMAIN}"
    log_info "DNS provider: $DNS_PROVIDER"

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
