#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
NON_INTERACTIVE="false"
ENABLE_INTERNAL=""
ENABLE_PRODUCTION=""

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
    cat <<'EOF'
setup-play-publishing-secrets.sh

Interactive helper for Google Play publishing setup. It can:
  - help locate the Play service-account JSON, Android release keystore, and google-services.json
  - collect the required keystore passwords and alias
  - upload the resulting values to GitHub Actions secrets via `gh secret set`
  - optionally enable internal and production publish paths independently

Usage:
  ./scripts/setup-play-publishing-secrets.sh [--non-interactive] [--enable-internal true|false] [--enable-production true|false]
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --non-interactive) NON_INTERACTIVE="true"; shift ;;
            --enable-internal) ENABLE_INTERNAL="$2"; shift 2 ;;
            --enable-production) ENABLE_PRODUCTION="$2"; shift 2 ;;
            --enable-workflow) ENABLE_INTERNAL="$2"; shift 2 ;;
            -h|--help) print_usage; exit 0 ;;
            *) die "Unknown flag: $1" ;;
        esac
    done
}

prompt_value() {
    local prompt="$1"
    local default="${2:-}"
    local value=""
    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        printf '%s' "$default"
        return 0
    fi
    if [[ -n "$default" ]]; then
        read -rp "$prompt [$default]: " value
        printf '%s' "${value:-$default}"
    else
        read -rp "$prompt: " value
        printf '%s' "$value"
    fi
}

prompt_secret() {
    local prompt="$1"
    local value=""
    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        printf ''
        return 0
    fi
    read -rsp "$prompt: " value
    echo >&2
    printf '%s' "$value"
}

confirm() {
    local prompt="$1"
    local default="${2:-y}"
    local reply=""
    if [[ "$NON_INTERACTIVE" == "true" ]]; then
        [[ "$default" == "y" ]]
        return
    fi
    read -rp "$prompt [$default]: " reply
    reply="${reply:-$default}"
    reply="$(printf '%s' "$reply" | tr '[:upper:]' '[:lower:]')"
    [[ "$reply" == "y" || "$reply" == "yes" ]]
}

require_gh() {
    command -v gh >/dev/null 2>&1 || die "GitHub CLI ('gh') is required."
    gh auth status >/dev/null 2>&1 || die "Run 'gh auth login' before using this helper."
}

collect_candidates() {
    local pattern="$1"
    shift
    local roots=("$@")
    local candidates=()
    local root
    for root in "${roots[@]}"; do
        [[ -d "$root" ]] || continue
        while IFS= read -r file; do
            candidates+=("$file")
        done < <(find "$root" -maxdepth 3 -type f $pattern 2>/dev/null | sort)
    done
    printf '%s\n' "${candidates[@]}"
}

pick_file() {
    local label="$1"
    local pattern="$2"
    shift 2
    local roots=("$@")
    local candidates=()
    local choice=""
    local manual=""

    while IFS= read -r candidate; do
        [[ -n "$candidate" ]] && candidates+=("$candidate")
    done < <(collect_candidates "$pattern" "${roots[@]}")

    if [[ "${#candidates[@]}" -gt 0 && "$NON_INTERACTIVE" != "true" ]]; then
        printf '%s\n' "$label candidates:"
        local i
        for i in "${!candidates[@]}"; do
            printf '  %d. %s\n' "$((i + 1))" "${candidates[$i]}"
        done
        printf '  %d. Enter a path manually\n' "$((${#candidates[@]} + 1))"
        while :; do
            read -rp "Select $label source: " choice
            if [[ "$choice" =~ ^[0-9]+$ ]]; then
                if (( choice >= 1 && choice <= ${#candidates[@]} )); then
                    printf '%s' "${candidates[$((choice - 1))]}"
                    return 0
                fi
                if (( choice == ${#candidates[@]} + 1 )); then
                    break
                fi
            fi
            log_warn "Choose a listed number."
        done
    fi

    manual="$(prompt_value "Path to $label")"
    [[ -f "$manual" ]] || die "$label file not found: $manual"
    printf '%s' "$manual"
}

validate_json_file() {
    local path="$1"
    local required_fragment="$2"
    grep -q "$required_fragment" "$path" || die "Expected '$required_fragment' in $path"
}

upload_secret_from_file() {
    local name="$1"
    local file="$2"
    gh secret set "$name" < "$file"
    log_info "Set GitHub secret $name from $file"
}

upload_secret_from_body() {
    local name="$1"
    local value="$2"
    gh secret set "$name" --body "$value"
    log_info "Set GitHub secret $name"
}

maybe_set_publish_variable() {
    local variable_name="$1"
    local should_enable="$2"
    if [[ "$should_enable" == "true" ]]; then
        gh variable set "$variable_name" --body "true"
        log_info "Enabled Play publishing via repository variable ${variable_name}=true"
    else
        log_warn "Leaving ${variable_name} unchanged."
    fi
}

main() {
    parse_args "$@"
    require_gh
    cd "$REPO_ROOT"

    log_phase "Collect Google Play publishing inputs"

    local play_json
    play_json="$(
        pick_file \
            "Play service-account JSON" \
            "\\( -name '*.json' \\)" \
            "$HOME/Downloads" \
            "$HOME/Documents" \
            "$REPO_ROOT"
    )"
    validate_json_file "$play_json" '"type"[[:space:]]*:[[:space:]]*"service_account"'

    local google_services_json
    google_services_json="$(
        pick_file \
            "google-services.json" \
            "\\( -name 'google-services.json' \\)" \
            "$REPO_ROOT/app/android-main" \
            "$HOME/Downloads" \
            "$HOME/Documents"
    )"
    validate_json_file "$google_services_json" '"project_info"'

    local keystore_path
    keystore_path="$(
        pick_file \
            "Android release keystore" \
            "\\( -name '*.jks' -o -name '*.keystore' \\)" \
            "$HOME/Downloads" \
            "$HOME/Documents" \
            "$REPO_ROOT"
    )"

    local store_password
    store_password="$(prompt_secret "Release keystore password")"
    [[ -n "$store_password" ]] || die "Release keystore password is required."

    local key_alias
    key_alias="$(prompt_value "Release key alias")"
    [[ -n "$key_alias" ]] || die "Release key alias is required."

    local key_password
    key_password="$(prompt_secret "Release key password")"
    [[ -n "$key_password" ]] || die "Release key password is required."

    log_phase "Upload GitHub Actions secrets"
    upload_secret_from_file "ANDROID_PUBLISHER_CREDENTIALS" "$play_json"
    upload_secret_from_file "LOGDATE_ANDROID_GOOGLE_SERVICES_JSON" "$google_services_json"
    upload_secret_from_body "LOGDATE_RELEASE_STORE_BASE64" "$(base64 < "$keystore_path" | tr -d '\n')"
    upload_secret_from_body "LOGDATE_RELEASE_STORE_PASSWORD" "$store_password"
    upload_secret_from_body "LOGDATE_RELEASE_KEY_ALIAS" "$key_alias"
    upload_secret_from_body "LOGDATE_RELEASE_KEY_PASSWORD" "$key_password"

    local enable_internal="$ENABLE_INTERNAL"
    if [[ -z "$enable_internal" ]]; then
        if confirm "Enable automated internal-track publishing after this setup?" "n"; then
            enable_internal="true"
        else
            enable_internal="false"
        fi
    fi

    local enable_production="$ENABLE_PRODUCTION"
    if [[ -z "$enable_production" ]]; then
        if confirm "Enable production publishing from android-v* tags after this setup?" "n"; then
            enable_production="true"
        else
            enable_production="false"
        fi
    fi

    maybe_set_publish_variable "LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED" "$enable_internal"
    maybe_set_publish_variable "LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED" "$enable_production"

    log_phase "Next steps"
    log_info "Internal publishing runs from every main commit and workflow_dispatch once LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED=true."
    log_info "Production publishing now promotes the matching internal release from android-v<major>.<minor>.<patch> tag pushes once LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED=true."
    log_info "Tag only commits that have already been published successfully to the internal track."
    log_info "Google Play still requires the very first app upload to be done manually in Play Console."
}

main "$@"
