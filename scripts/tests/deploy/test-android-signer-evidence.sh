#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
cd "$repository_root"

failures=0

record_failure() {
  printf 'FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

source_sha="$(git rev-parse HEAD)"
private_root="$(mktemp -d)"
trap 'rm -R -- "$private_root"' EXIT
chmod 700 "$private_root"

for environment in staging production; do
  evidence_file="infra/android-signing/${environment}-signer-evidence.json"
  rendered_contract="$private_root/${environment}-cloud-contract.json"

  if [[ ! -f "$evidence_file" ]]; then
    record_failure "canonical $environment signer evidence is absent at $evidence_file"
  fi

  if ! bash scripts/render-cloud-run-contract.sh \
    --environment "$environment" \
    --release-sha "$source_sha" >"$rendered_contract" 2>"$private_root/${environment}-render.stderr"; then
    record_failure "the selected $environment inputs cannot render a commit-bound signing contract"
  fi
done

if [[ ! -x scripts/android-signer-evidence.sh ]]; then
  record_failure "scripts/android-signer-evidence.sh is absent or not executable"
fi

if ((failures > 0)); then
  printf 'Observed %d signer-evidence contract failure(s).\n' "$failures" >&2
  exit 1
fi

for environment in staging production; do
  bash scripts/android-signer-evidence.sh validate \
    --environment "$environment" \
    --evidence-file "$repository_root/infra/android-signing/${environment}-signer-evidence.json" \
    --rendered-contract "$private_root/${environment}-cloud-contract.json" \
    --source-sha "$source_sha"
done

printf 'Android signer evidence contract passed.\n'
