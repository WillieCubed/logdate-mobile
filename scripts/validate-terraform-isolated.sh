#!/usr/bin/env bash
# Validate Terraform without consulting checkout or remote-backend metadata.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="$(cd "$SCRIPT_DIR/../infra/terraform" && pwd)"
ISOLATED_TF_DATA_DIR="$(mktemp -d)"

cleanup() {
    rm -rf "$ISOLATED_TF_DATA_DIR"
}
trap cleanup EXIT

chmod 700 "$ISOLATED_TF_DATA_DIR"
while IFS= read -r environment_name; do
    if [[ "$environment_name" == TF_CLI_ARGS* || "$environment_name" == TF_VAR_* ]]; then
        unset "$environment_name"
    fi
done < <(compgen -e)
unset TF_WORKSPACE TF_LOG TF_LOG_PATH
export TF_INPUT=0
export TF_DATA_DIR="$ISOLATED_TF_DATA_DIR"

terraform -chdir="$TF_DIR" init -backend=false -input=false >"$ISOLATED_TF_DATA_DIR/init.log" 2>&1 || {
    status=$?
    printf 'ERROR: Terraform initialization failed.\n' >&2
    exit "$status"
}
terraform -chdir="$TF_DIR" validate >"$ISOLATED_TF_DATA_DIR/validate.log" 2>&1 || {
    status=$?
    printf 'ERROR: Terraform validation failed.\n' >&2
    exit "$status"
}
