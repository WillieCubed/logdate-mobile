#!/usr/bin/env bash
# shellcheck disable=SC2016 # the `$` in single-quoted fixtures is literal on purpose
# The pre-commit secret rules: real secrets are caught, and code that merely
# names or reads a secret is not.
set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root
source scripts/validation/sensitive-content.sh

flagged_path() { is_sensitive_path "$1" || fail "expected $1 to be flagged"; pass; }
allowed_path() { ! is_sensitive_path "$1" || fail "expected $1 to be allowed"; pass; }
flagged_text() { text_has_secret "$1" || fail "expected to flag: $1"; pass; }
allowed_text() { ! text_has_secret "$1" || fail "expected to allow: $1"; pass; }

flagged_path ".env"
flagged_path "infra/terraform/production.env"
flagged_path "app/android-main/google-services.json"
flagged_path "keys/upload.jks"
flagged_path "local.properties"
flagged_path "config/apikey.txt"
flagged_path "deploy/client_secret.json"
flagged_path "deploy/secrets/prod.json"
flagged_path "apikeys/maps.txt"
flagged_path "k8s/secrets.yaml"
flagged_path "config/app-secrets.yml"
flagged_path ".github/workflows/rotate-secrets.yml"

allowed_path "scripts/upload-play-keystore.sh"
allowed_path "scripts/sync-secrets.sh"
allowed_path "docs/runbook/release-secrets.md"
allowed_path "server/src/main/kotlin/SecretStore.kt"
allowed_path "scripts/setup/environments/production.conf"

flagged_text 'password = "hunter2hunter2"'
flagged_text "api_key: 'abcdefgh12345678'"
flagged_text 'DATABASE_URL=postgresql://logdate:s3cretpass@db.internal/logdate'
flagged_text 'jdbc:postgresql://host/db?password=abcdef123'
flagged_text 'AKIAABCDEFGHIJKLMNOP'
flagged_text "$(printf '%s' '{"type": "service' '_account"}')"

allowed_text 'store_password="$(read_env_value "$FILE" STORE_PASSWORD)"'
allowed_text 'token="${GOOGLE_API_TOKEN:-}"'
allowed_text 'val password = "${config.password}"'
allowed_text 'password = ""'
allowed_text 'token: ${{ secrets.GITHUB_TOKEN }}'

skips_content_scan "scripts/tests/deploy/test-foo.sh" || fail "scripts/tests fixtures should be skipped"
pass
skips_content_scan "client/data/src/commonTest/kotlin/FooTest.kt" || fail "test source sets should be skipped"
pass
! skips_content_scan "scripts/setup/main.sh" || fail "scripts/setup should be scanned"
pass

print_pass_summary "pre-commit secret rules"
