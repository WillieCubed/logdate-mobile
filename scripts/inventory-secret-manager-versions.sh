#!/usr/bin/env bash
# Print value-free enabled-version metadata for the seven required server secrets only.

set -euo pipefail

ENVIRONMENT=""
WORK_DIR=""

die() {
    printf 'INVENTORY_FAILURE_CODE=%s\n' "$1" >&2
    exit 1
}

run_gcloud() {
    local failure_code="$1" output_file="$2"
    shift 2
    if ! gcloud "$@" >"$output_file" 2>"$WORK_DIR/gcloud.stderr"; then
        die "$failure_code"
    fi
}

cleanup() {
    if [[ -n "$WORK_DIR" ]]; then
        rm -rf "$WORK_DIR"
    fi
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
    case "$1" in
        --environment)
            [[ $# -ge 2 ]] || die invalid_metadata
            ENVIRONMENT="$2"
            shift 2
            ;;
        *) die invalid_metadata ;;
    esac
done

case "$ENVIRONMENT" in
    staging) PROJECT_ID="logdate-dev" ;;
    production) PROJECT_ID="logdate" ;;
    *) die invalid_metadata ;;
esac

command -v gcloud >/dev/null 2>&1 || die invalid_metadata
WORK_DIR="$(mktemp -d)"
chmod 700 "$WORK_DIR"

ACTIVE_PRINCIPAL_FILE="$WORK_DIR/active-principal"
TARGET_PROJECT_FILE="$WORK_DIR/target-project"
run_gcloud identity_mismatch "$ACTIVE_PRINCIPAL_FILE" auth list --filter='status:ACTIVE' --format='value(account)' --quiet
run_gcloud target_project_unavailable "$TARGET_PROJECT_FILE" projects describe "$PROJECT_ID" --format='value(projectId)' --quiet

ACTIVE_PRINCIPAL="$(LC_ALL=C sort -u "$ACTIVE_PRINCIPAL_FILE")"
TARGET_PROJECT="$(LC_ALL=C sort -u "$TARGET_PROJECT_FILE")"
EXPECTED_PRINCIPAL="github-deploy@${PROJECT_ID}.iam.gserviceaccount.com"

[[ "$ACTIVE_PRINCIPAL" == "$EXPECTED_PRINCIPAL" ]] || die identity_mismatch
[[ "$TARGET_PROJECT" == "$PROJECT_ID" ]] || die invalid_metadata

printf 'environment=%s\nproject_id=%s\n' "$ENVIRONMENT" "$PROJECT_ID"

for secret_id in \
    logdate-db-url \
    logdate-db-user \
    logdate-db-password \
    logdate-jwt-secret \
    logdate-server-encryption-key \
    logdate-server-encryption-key-id \
    logdate-health-internal-token; do
    VERSIONS_FILE="$WORK_DIR/versions-$secret_id"
    run_gcloud metadata_access_denied_or_missing "$VERSIONS_FILE" secrets versions list "$secret_id" \
        --project "$PROJECT_ID" \
        --filter='state:ENABLED' \
        --format='value(name)' \
        --quiet

    printf 'secret_id=%s\n' "$secret_id"
    VERSION_IDS_FILE="$WORK_DIR/version-ids-$secret_id"
    while IFS= read -r version_name; do
        [[ -n "$version_name" ]] || continue
        expected_prefix="projects/${PROJECT_ID}/secrets/${secret_id}/versions/"
        [[ "$version_name" == "$expected_prefix"* ]] || die invalid_metadata
        version_id="${version_name#"$expected_prefix"}"
        [[ "$version_id" =~ ^[1-9][0-9]*$ ]] || die invalid_metadata
        printf '%s\n' "$version_id" >>"$VERSION_IDS_FILE"
    done <"$VERSIONS_FILE"
    [[ -s "$VERSION_IDS_FILE" ]] || die invalid_metadata
    while IFS= read -r version_id; do
        printf 'enabled_version=%s\n' "$version_id"
    done < <(LC_ALL=C sort -n -u "$VERSION_IDS_FILE")
done
