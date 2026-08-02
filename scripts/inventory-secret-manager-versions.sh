#!/usr/bin/env bash
# Print value-free enabled-version metadata for the seven required server secrets only.

set -euo pipefail

ENVIRONMENT=""
WORK_DIR=""

die() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
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
            [[ $# -ge 2 ]] || die '--environment requires a value.'
            ENVIRONMENT="$2"
            shift 2
            ;;
        *) die "unknown argument: $1" ;;
    esac
done

case "$ENVIRONMENT" in
    staging) PROJECT_ID="logdate-dev" ;;
    production) PROJECT_ID="logdate" ;;
    *) die 'environment must be staging or production.' ;;
esac

command -v gcloud >/dev/null 2>&1 || die 'gcloud is required.'
WORK_DIR="$(mktemp -d)"
chmod 700 "$WORK_DIR"

ACTIVE_PRINCIPAL_FILE="$WORK_DIR/active-principal"
TARGET_PROJECT_FILE="$WORK_DIR/target-project"
gcloud auth list --filter='status:ACTIVE' --format='value(account)' --quiet >"$ACTIVE_PRINCIPAL_FILE"
gcloud projects describe "$PROJECT_ID" --format='value(projectId)' --quiet >"$TARGET_PROJECT_FILE"

ACTIVE_PRINCIPAL="$(LC_ALL=C sort -u "$ACTIVE_PRINCIPAL_FILE")"
TARGET_PROJECT="$(LC_ALL=C sort -u "$TARGET_PROJECT_FILE")"
EXPECTED_PRINCIPAL="github-deploy@${PROJECT_ID}.iam.gserviceaccount.com"

[[ "$ACTIVE_PRINCIPAL" == "$EXPECTED_PRINCIPAL" ]] || die 'authenticated principal does not match the expected GitHub deploy identity.'
[[ "$TARGET_PROJECT" == "$PROJECT_ID" ]] || die 'target project does not match the selected environment.'

printf 'environment=%s\nproject_id=%s\nauthenticated_principal=%s\n' \
    "$ENVIRONMENT" "$PROJECT_ID" "$ACTIVE_PRINCIPAL"

for secret_id in \
    logdate-db-url \
    logdate-db-user \
    logdate-db-password \
    logdate-jwt-secret \
    logdate-server-encryption-key \
    logdate-server-encryption-key-id \
    logdate-health-internal-token; do
    VERSIONS_FILE="$WORK_DIR/versions-$secret_id"
    gcloud secrets versions list "$secret_id" \
        --project "$PROJECT_ID" \
        --filter='state:ENABLED' \
        --format='value(name)' \
        --quiet >"$VERSIONS_FILE"

    printf 'secret_id=%s\n' "$secret_id"
    VERSION_IDS_FILE="$WORK_DIR/version-ids-$secret_id"
    while IFS= read -r version_name; do
        [[ -n "$version_name" ]] || continue
        expected_prefix="projects/${PROJECT_ID}/secrets/${secret_id}/versions/"
        [[ "$version_name" == "$expected_prefix"* ]] || die 'Secret Manager returned an invalid enabled version resource name.'
        version_id="${version_name#"$expected_prefix"}"
        [[ "$version_id" =~ ^[1-9][0-9]*$ ]] || die 'Secret Manager returned a non-numeric enabled version ID.'
        printf '%s\n' "$version_id" >>"$VERSION_IDS_FILE"
    done <"$VERSIONS_FILE"
    [[ -s "$VERSION_IDS_FILE" ]] || die 'Secret Manager returned no enabled numeric version for a required secret.'
    while IFS= read -r version_id; do
        printf 'enabled_version=%s\n' "$version_id"
    done < <(LC_ALL=C sort -n -u "$VERSION_IDS_FILE")
done
