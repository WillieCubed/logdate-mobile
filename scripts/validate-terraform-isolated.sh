#!/usr/bin/env bash
# Validate Terraform without consulting checkout or remote-backend metadata.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TF_DIR="$(cd "$SCRIPT_DIR/../infra/terraform" && pwd)"
PRIVATE_WORK_DIR="$(mktemp -d)"
ISOLATED_TF_DATA_DIR="$PRIVATE_WORK_DIR/terraform-data"
PRIVATE_CONFIG_DIR="$PRIVATE_WORK_DIR/config"

cleanup() {
    rm -rf "$PRIVATE_WORK_DIR"
}
trap cleanup EXIT

chmod 700 "$PRIVATE_WORK_DIR"
mkdir -p "$ISOLATED_TF_DATA_DIR" "$PRIVATE_CONFIG_DIR"
chmod 700 "$ISOLATED_TF_DATA_DIR" "$PRIVATE_CONFIG_DIR"
shopt -s nullglob
terraform_files=("$TF_DIR"/*.tf)
[[ ${#terraform_files[@]} -gt 0 ]] || {
    printf 'ERROR: Terraform configuration is missing.\n' >&2
    exit 1
}
for terraform_file in "${terraform_files[@]}"; do
    cp "$terraform_file" "$PRIVATE_CONFIG_DIR/${terraform_file##*/}"
done
if [[ -f "$TF_DIR/.terraform.lock.hcl" ]]; then
    cp "$TF_DIR/.terraform.lock.hcl" "$PRIVATE_CONFIG_DIR/.terraform.lock.hcl"
fi
while IFS= read -r environment_name; do
    if [[ "$environment_name" == TF_* ]]; then
        unset "$environment_name"
    fi
done < <(compgen -e)
unset TF_WORKSPACE TF_LOG TF_LOG_PATH
export TF_INPUT=0
export TF_DATA_DIR="$ISOLATED_TF_DATA_DIR"

terraform -chdir="$PRIVATE_CONFIG_DIR" init -backend=false -input=false >"$PRIVATE_WORK_DIR/init.log" 2>&1 || {
    status=$?
    printf 'ERROR: Terraform initialization failed.\n' >&2
    exit "$status"
}
terraform -chdir="$PRIVATE_CONFIG_DIR" validate >"$PRIVATE_WORK_DIR/validate.log" 2>&1 || {
    status=$?
    printf 'ERROR: Terraform validation failed.\n' >&2
    exit "$status"
}
