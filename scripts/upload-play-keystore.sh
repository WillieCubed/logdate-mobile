#!/usr/bin/env bash
set -euo pipefail
# This script holds signing passwords in variables; `bash -x` would print them.
{ set +x; } 2>/dev/null

# shellcheck source=scripts/lib/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
REPO_ROOT="$LOGDATE_REPO_ROOT"
SIGNING_DIR="${LOGDATE_SIGNING_DIR:-$HOME/.logdate-signing}"

NON_INTERACTIVE="false"
KEYLESS="false"
GH_ENVIRONMENT="production"
SERVICE_ACCOUNT_FILE=""
UPLOAD_ENV_FILE="$SIGNING_DIR/production-upload.env"
KEYSTORE_FILE=""
ENABLE_INTERNAL=""
ENABLE_PRODUCTION=""

print_usage() {
    cat <<EOF
upload-play-keystore.sh

Uploads the Google Play publishing inputs to a GitHub Actions environment:
  ANDROID_PUBLISHER_CREDENTIALS    Play service-account JSON
  LOGDATE_RELEASE_STORE_BASE64     upload keystore, base64-encoded
  LOGDATE_RELEASE_STORE_PASSWORD   } read from the upload env file written by
  LOGDATE_RELEASE_KEY_ALIAS        } scripts/create-signing-keystore.sh, or
  LOGDATE_RELEASE_KEY_PASSWORD     } prompted for interactively

Every value reaches 'gh secret set' on stdin. None is placed in argv, where
other processes and shell tracing could see it, and none is printed.

Usage:
  ./scripts/upload-play-keystore.sh [options]

Options:
  --env NAME                  GitHub environment to write to (default: production)
  --service-account FILE      Play service-account JSON
                              (default: $SIGNING_DIR/play-publisher.json)
  --keyless                   Skip ANDROID_PUBLISHER_CREDENTIALS; CI impersonates
                              the service account through Workload Identity
  --upload-env FILE           Upload key env file (default: $SIGNING_DIR/production-upload.env)
  --keystore FILE             Upload keystore (default: LOGDATE_RELEASE_STORE_FILE from the env file)
  --non-interactive           Never prompt; fail when an input is missing
  --enable-internal BOOL      Set LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED
  --enable-production BOOL    Set LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --env) GH_ENVIRONMENT="${2:?--env needs a value}"; shift 2 ;;
            --service-account) SERVICE_ACCOUNT_FILE="${2:?--service-account needs a value}"; shift 2 ;;
            --upload-env) UPLOAD_ENV_FILE="${2:?--upload-env needs a value}"; shift 2 ;;
            --keystore) KEYSTORE_FILE="${2:?--keystore needs a value}"; shift 2 ;;
            --non-interactive) NON_INTERACTIVE="true"; shift ;;
            --keyless) KEYLESS="true"; shift ;;
            --enable-internal) ENABLE_INTERNAL="${2:?--enable-internal needs a value}"; shift 2 ;;
            --enable-production) ENABLE_PRODUCTION="${2:?--enable-production needs a value}"; shift 2 ;;
            -h|--help) print_usage; exit 0 ;;
            *) die "Unknown flag: $1" ;;
        esac
    done
}

require_gh() {
    command -v gh >/dev/null 2>&1 || die "GitHub CLI ('gh') is required."
    gh auth status >/dev/null 2>&1 || die "Run 'gh auth login' before using this helper."
}

# Reads one key from a KEY=value file with the same rules as the Gradle
# signingEnvFile parser: blank and '#' lines skipped, whitespace and one layer
# of quotes trimmed.
read_env_value() {
    local file="$1"
    local wanted="$2"
    local line key value
    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line#"${line%%[![:space:]]*}"}"
        [[ -z "$line" || "$line" == \#* || "$line" != *=* ]] && continue
        key="${line%%=*}"
        key="${key%"${key##*[![:space:]]}"}"
        [[ "$key" == "$wanted" ]] || continue
        value="${line#*=}"
        value="${value#"${value%%[![:space:]]*}"}"
        value="${value%"${value##*[![:space:]]}"}"
        value="${value#[\"\']}"
        value="${value%[\"\']}"
        printf '%s' "$value"
        return 0
    done < "$file"
    return 1
}

set_secret_from_stdin() {
    local name="$1"
    gh secret set "$name" --env "$GH_ENVIRONMENT"
    log_info "Set $name in environment '$GH_ENVIRONMENT'"
}

maybe_set_publish_variable() {
    local variable_name="$1"
    local should_enable="$2"
    # Repo-level on purpose: the publish jobs gate on these in their job-level
    # 'if:', which is evaluated before an environment's variables are loaded.
    if [[ "$should_enable" == "true" ]]; then
        gh variable set "$variable_name" --body "true"
        log_info "Set repository variable ${variable_name}=true"
    else
        log_warn "Leaving ${variable_name} unchanged."
    fi
}

main() {
    parse_args "$@"
    require_gh
    cd "$REPO_ROOT"

    gh api "repos/{owner}/{repo}/environments/$GH_ENVIRONMENT" >/dev/null 2>&1 \
        || die "GitHub environment '$GH_ENVIRONMENT' does not exist."

    log_phase "Collect Google Play publishing inputs"

    if [[ "$KEYLESS" != "true" ]]; then
        if [[ -z "$SERVICE_ACCOUNT_FILE" ]]; then
            SERVICE_ACCOUNT_FILE="$SIGNING_DIR/play-publisher.json"
            [[ -f "$SERVICE_ACCOUNT_FILE" ]] || SERVICE_ACCOUNT_FILE="$(prompt_value "Path to Play service-account JSON")"
        fi
        [[ -f "$SERVICE_ACCOUNT_FILE" ]] || die "Service-account JSON not found: $SERVICE_ACCOUNT_FILE"
        grep -q '"type"[[:space:]]*:[[:space:]]*"service_account"' "$SERVICE_ACCOUNT_FILE" \
            || die "$SERVICE_ACCOUNT_FILE is not a service-account key."
    fi

    local store_pass="" key_alias="" key_pass=""
    if [[ -f "$UPLOAD_ENV_FILE" ]]; then
        log_info "Reading upload key settings from $UPLOAD_ENV_FILE"
        [[ -n "$KEYSTORE_FILE" ]] || KEYSTORE_FILE="$(read_env_value "$UPLOAD_ENV_FILE" LOGDATE_RELEASE_STORE_FILE || true)"
        store_pass="$(read_env_value "$UPLOAD_ENV_FILE" LOGDATE_RELEASE_STORE_PASSWORD || true)"
        key_alias="$(read_env_value "$UPLOAD_ENV_FILE" LOGDATE_RELEASE_KEY_ALIAS || true)"
        key_pass="$(read_env_value "$UPLOAD_ENV_FILE" LOGDATE_RELEASE_KEY_PASSWORD || true)"
    else
        log_warn "No upload env file at $UPLOAD_ENV_FILE; prompting instead."
    fi

    [[ -n "$KEYSTORE_FILE" ]] || KEYSTORE_FILE="$(prompt_value "Path to upload keystore")"
    [[ -f "$KEYSTORE_FILE" ]] || die "Upload keystore not found: $KEYSTORE_FILE"
    [[ -n "$store_pass" ]] || store_pass="$(prompt_secret "Upload keystore password")"
    [[ -n "$key_alias" ]] || key_alias="$(prompt_value "Upload key alias")"
    [[ -n "$key_pass" ]] || key_pass="$(prompt_secret "Upload key password")"
    [[ -n "$store_pass" && -n "$key_alias" && -n "$key_pass" ]] \
        || die "The keystore password, key alias, and key password are all required."

    log_phase "Upload secrets to GitHub environment '$GH_ENVIRONMENT'"
    [[ "$KEYLESS" == "true" ]] || set_secret_from_stdin ANDROID_PUBLISHER_CREDENTIALS < "$SERVICE_ACCOUNT_FILE"
    base64 < "$KEYSTORE_FILE" | tr -d '\n' | set_secret_from_stdin LOGDATE_RELEASE_STORE_BASE64
    printf '%s' "$store_pass" | set_secret_from_stdin LOGDATE_RELEASE_STORE_PASSWORD
    printf '%s' "$key_alias" | set_secret_from_stdin LOGDATE_RELEASE_KEY_ALIAS
    printf '%s' "$key_pass" | set_secret_from_stdin LOGDATE_RELEASE_KEY_PASSWORD

    log_info "Firebase google-services.json upload is handled by scripts/sync-firebase-configs.sh"

    local enable_internal="$ENABLE_INTERNAL"
    if [[ -z "$enable_internal" ]]; then
        if confirm "Publish every main commit to the Play internal track?"; then
            enable_internal="true"
        else
            enable_internal="false"
        fi
    fi
    local enable_production="$ENABLE_PRODUCTION"
    if [[ -z "$enable_production" ]]; then
        if confirm "Promote to Play production from android-v* tags?"; then
            enable_production="true"
        else
            enable_production="false"
        fi
    fi
    maybe_set_publish_variable LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED "$enable_internal"
    maybe_set_publish_variable LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED "$enable_production"

    log_phase "Next steps"
    log_info "Google Play requires the very first bundle to be uploaded by hand in Play Console."
    log_info "Tag only commits that already reached the internal track; tags promote, they do not rebuild."
}

main "$@"
